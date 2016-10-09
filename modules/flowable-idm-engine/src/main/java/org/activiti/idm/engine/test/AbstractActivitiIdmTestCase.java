/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.idm.engine.test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.activiti.idm.api.IdmIdentityService;
import org.activiti.idm.api.IdmManagementService;
import org.activiti.idm.engine.IdmEngine;
import org.activiti.idm.engine.IdmEngineConfiguration;
import org.activiti.idm.engine.impl.db.DbSqlSession;
import org.activiti.idm.engine.impl.interceptor.Command;
import org.activiti.idm.engine.impl.interceptor.CommandConfig;
import org.activiti.idm.engine.impl.interceptor.CommandContext;
import org.activiti.idm.engine.impl.interceptor.CommandExecutor;
import org.junit.Assert;

import junit.framework.AssertionFailedError;

/**
 * @author Tijs Rademakers
 * @author Joram Barrez
 */
public abstract class AbstractActivitiIdmTestCase extends AbstractTestCase {

  private static final List<String> TABLENAMES_EXCLUDED_FROM_DB_CLEAN_CHECK = Collections.singletonList("ACT_GE_PROPERTY");

  protected IdmEngine idmEngine;

  protected Throwable exception;

  protected IdmEngineConfiguration idmEngineConfiguration;
  protected IdmIdentityService idmIdentityService;
  protected IdmManagementService idmManagementService;

  protected abstract void initializeIdmEngine();

  // Default: do nothing
  protected void closeDownIdmEngine() {
  }

  @Override
  public void runBare() throws Throwable {
    initializeIdmEngine();
    if (idmIdentityService == null) {
      initializeServices();
    }

    try {
      super.runBare();

    } catch (AssertionFailedError e) {
      log.error(EMPTY_LINE);
      log.error("ASSERTION FAILED: {}", e, e);
      exception = e;
      throw e;

    } catch (Throwable e) {
      log.error(EMPTY_LINE);
      log.error("EXCEPTION: {}", e, e);
      exception = e;
      throw e;

    } finally {
      
      assertAndEnsureCleanDb();
      idmEngineConfiguration.getClock().reset();

      // Can't do this in the teardown, as the teardown will be called as part of the super.runBare
      closeDownIdmEngine();
    }
  }
  
  /**
   * Each test is assumed to clean up all DB content it entered. After a test method executed, this method scans all tables to see if the DB is completely clean. It throws AssertionFailed in case the
   * DB is not clean. If the DB is not clean, it is cleaned by performing a create a drop.
   */
  protected void assertAndEnsureCleanDb() throws Throwable {
    log.debug("verifying that db is clean after test");
    Map<String, Long> tableCounts = idmManagementService.getTableCount();
    StringBuilder outputMessage = new StringBuilder();
    for (String tableName : tableCounts.keySet()) {
      String tableNameWithoutPrefix = tableName.replace(idmEngineConfiguration.getDatabaseTablePrefix(), "");
      if (!TABLENAMES_EXCLUDED_FROM_DB_CLEAN_CHECK.contains(tableNameWithoutPrefix)) {
        Long count = tableCounts.get(tableName);
        if (count != 0L) {
          outputMessage.append("  ").append(tableName).append(": ").append(count).append(" record(s) ");
        }
      }
    }
    if (outputMessage.length() > 0) {
      outputMessage.insert(0, "DB NOT CLEAN: \n");
      log.error(EMPTY_LINE);
      log.error(outputMessage.toString());

      log.info("dropping and recreating db");

      CommandExecutor commandExecutor = idmEngine.getIdmEngineConfiguration().getCommandExecutor();
      CommandConfig config = new CommandConfig().transactionNotSupported();
      commandExecutor.execute(config, new Command<Object>() {
        public Object execute(CommandContext commandContext) {
          DbSqlSession session = commandContext.getDbSqlSession();
          session.dbSchemaDrop();
          session.dbSchemaCreate();
          return null;
        }
      });

      if (exception != null) {
        throw exception;
      } else {
        Assert.fail(outputMessage.toString());
      }
    } else {
      log.info("database was clean");
    }
  }

  protected void initializeServices() {
    idmEngineConfiguration = idmEngine.getIdmEngineConfiguration();
    idmIdentityService = idmEngine.getIdmIdentityService();
    idmManagementService = idmEngine.getIdmManagementService();
  }
  
}
