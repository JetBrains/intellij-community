/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.impl.module;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerConfigurationType;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.impl.configuration.deployment.ModuleDeploymentSourceImpl;
import com.intellij.remoteServer.util.*;
import com.intellij.remoteServer.util.ssh.SshKeyChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class CloudModuleBuilderContributionBase<
  SC extends CloudConfigurationBase,
  DC extends CloudDeploymentNameConfiguration,
  AC extends CloudApplicationConfiguration,
  SR extends CloudMultiSourceServerRuntimeInstance<DC, ?, ?, ?>>
  extends CloudModuleBuilderContribution {

  @Override
  public void configureModule(Module module,
                              RemoteServer<?> account,
                              CloudApplicationConfiguration applicationConfiguration) {
    RemoteServer<SC> castedAccount = (RemoteServer<SC>)account;
    final AC castedApplicationConfiguration = (AC)applicationConfiguration;

    DC deploymentConfiguration = createDeploymentConfiguration();

    if (applicationConfiguration.isExisting()) {
      deploymentConfiguration.setDefaultDeploymentName(false);
      deploymentConfiguration.setDeploymentName(applicationConfiguration.getExistingAppName());
    }

    final DeployToServerRunConfiguration<SC, DC> runConfiguration = createRunConfiguration(module, castedAccount, deploymentConfiguration);

    final String cloudName = account.getType().getPresentableName();
    final Project project = module.getProject();
    new CloudConnectionTask<Object, SC, DC, SR>(project, CloudBundle.getText("cloud.support", cloudName), castedAccount) {

      CloudNotifier myNotifier = new CloudNotifier(cloudName);

      boolean myFirstAttempt = true;

      @Override
      protected Object run(SR serverRuntime) throws ServerRuntimeException {
        doConfigureModule(castedApplicationConfiguration, runConfiguration, myFirstAttempt, serverRuntime);
        myNotifier.showMessage(CloudBundle.getText("cloud.support.added", cloudName), MessageType.INFO);
        return null;
      }

      @Override
      protected void runtimeErrorOccurred(@NotNull String errorMessage) {
        myFirstAttempt = false;
        new SshKeyChecker().checkServerError(errorMessage, myNotifier, project, this);
      }
    }.performAsync();
  }

  private DeployToServerRunConfiguration<SC, DC> createRunConfiguration(Module module,
                                                                        RemoteServer<SC> server,
                                                                        DC deploymentConfiguration) {
    Project project = module.getProject();

    String serverName = server.getName();

    String name = generateRunConfigurationName(serverName, module.getName());

    final RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings runSettings
      = runManager.createRunConfiguration(name, getRunConfigurationType().getConfigurationFactories()[0]);

    final DeployToServerRunConfiguration<SC, DC> result = (DeployToServerRunConfiguration<SC, DC>)runSettings.getConfiguration();

    result.setServerName(serverName);

    final ModulePointer modulePointer = ModulePointerManager.getInstance(project).create(module);
    result.setDeploymentSource(new ModuleDeploymentSourceImpl(modulePointer));

    result.setDeploymentConfiguration(deploymentConfiguration);

    runManager.addConfiguration(runSettings, false);
    runManager.setSelectedConfiguration(runSettings);

    return result;
  }

  private static String generateRunConfigurationName(String serverName, String moduleName) {
    return CloudBundle.getText("run.configuration.name", serverName, moduleName);
  }

  private DeployToServerConfigurationType getRunConfigurationType() {
    String id = DeployToServerConfigurationType.getId(getCloudType());
    for (ConfigurationType configurationType : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions()) {
      if (configurationType instanceof DeployToServerConfigurationType) {
        DeployToServerConfigurationType deployConfigurationType = (DeployToServerConfigurationType)configurationType;
        if (deployConfigurationType.getId().equals(id)) {
          return deployConfigurationType;
        }
      }
    }
    return null;
  }

  @Override
  public abstract ServerType<SC> getCloudType();

  @Override
  public abstract CloudApplicationConfigurable<SC, DC, SR, AC> createApplicationConfigurable(@Nullable Project project,
                                                                                             Disposable parentDisposable);

  protected abstract DC createDeploymentConfiguration();

  protected abstract void doConfigureModule(AC applicationConfiguration,
                                            DeployToServerRunConfiguration<SC, DC> runConfiguration,
                                            boolean firstAttempt,
                                            SR serverRuntime) throws ServerRuntimeException;
}
