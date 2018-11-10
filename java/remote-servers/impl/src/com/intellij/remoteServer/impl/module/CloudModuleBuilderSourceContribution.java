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

import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromSourcesMode;
import com.intellij.ide.util.projectWizard.AbstractStepWithProgress;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import com.intellij.remoteServer.util.*;
import com.intellij.remoteServer.util.ssh.SshKeyChecker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class CloudModuleBuilderSourceContribution<
  SC extends CloudConfigurationBase,
  DC extends CloudDeploymentNameConfiguration,
  AC extends CloudSourceApplicationConfiguration,
  SR extends CloudMultiSourceServerRuntimeInstance<DC, ?, ?, ?>>
  extends CloudModuleBuilderContribution {

  private CloudNotifier myNotifier;

  public CloudModuleBuilderSourceContribution(CloudModuleBuilder moduleBuilder, ServerType<SC> cloudType) {
    super(moduleBuilder, cloudType);
  }

  @Override
  public void configureModule(final Module module) {
    final CloudModuleBuilder moduleBuilder = getModuleBuilder();
    RemoteServer<SC> account = (RemoteServer<SC>)moduleBuilder.getAccount();
    final AC applicationConfiguration = (AC)moduleBuilder.getApplicationConfiguration();

    DC deploymentConfiguration = createDeploymentConfiguration();

    if (applicationConfiguration.isExisting()) {
      deploymentConfiguration.setDefaultDeploymentName(false);
      deploymentConfiguration.setDeploymentName(applicationConfiguration.getExistingAppName());
    }

    final DeployToServerRunConfiguration<SC, DC> runConfiguration
      = CloudRunConfigurationUtil.createRunConfiguration(account, module, deploymentConfiguration);

    final ServerType<?> cloudType = account.getType();
    final Project project = module.getProject();
    new CloudConnectionTask<Object, SC, DC, SR>(project,
                                                CloudBundle.getText("cloud.support", cloudType.getPresentableName()),
                                                account) {

      boolean myFirstAttempt = true;

      @Override
      protected Object run(SR serverRuntime) throws ServerRuntimeException {
        doConfigureModule(applicationConfiguration, runConfiguration, myFirstAttempt, serverRuntime);
        return null;
      }

      @Override
      protected void runtimeErrorOccurred(@NotNull String errorMessage) {
        myFirstAttempt = false;
        new SshKeyChecker().checkServerError(errorMessage, getNotifier(), project, this);
      }

      @Override
      protected void postPerform(Object result) {
        detectModuleStructure(module, moduleBuilder.getContentEntryPath());
      }

      @Override
      protected boolean shouldStartInBackground() {
        return false;
      }
    }.performAsync();
  }

  private CloudNotifier getNotifier() {
    if (myNotifier == null) {
      myNotifier = new CloudNotifier(getCloudType().getPresentableName());
    }
    return myNotifier;
  }

  private void detectModuleStructure(Module module, final String contentPath) {
    final Project project = module.getProject();

    final CreateFromSourcesMode mode = new CreateFromSourcesMode() {

      @Override
      public boolean isAvailable(WizardContext context) {
        return true;
      }

      @Override
      public void addSteps(WizardContext context, ModulesProvider modulesProvider, StepSequence sequence, String specific) {
        super.addSteps(context, modulesProvider, sequence, specific);
        myProjectBuilder.setFileToImport(contentPath);
      }
    };

    final WizardContext context = new WizardContext(project, null);

    final StepSequence stepSequence = mode.getSteps(context, DefaultModulesProvider.createForProject(context.getProject()));
    if (stepSequence == null) {
      return;
    }

    Disposer.register(project, new Disposable() {

      @Override
      public void dispose() {
        for (ModuleWizardStep step : stepSequence.getAllSteps()) {
          step.disposeUIResources();
        }
      }
    });

    ProgressManager.getInstance()
      .run(new Task.Backgroundable(project, CloudBundle.getText("detect.module.structure", getCloudType().getPresentableName()), false) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          for (ModuleWizardStep step = ContainerUtil.getFirstItem(stepSequence.getSelectedSteps());
               step != null;
               step = stepSequence.getNextStep(step)) {
            if (step instanceof AbstractStepWithProgress<?>) {
              ((AbstractStepWithProgress)step).performStep();
            }
            else {
              step.updateDataModel();
            }
          }
          CloudAccountSelectionEditor.unsetAccountOnContext(context, getCloudType());
        }

        @Override
        public boolean shouldStartInBackground() {
          return false;
        }

        @Override
        public void onSuccess() {
          ProjectBuilder moduleBuilder = mode.getModuleBuilder();
          if (moduleBuilder == null) {
            return;
          }
          moduleBuilder.commit(project);
          getNotifier().showMessage(CloudBundle.getText("cloud.support.added", getCloudType().getPresentableName()), MessageType.INFO);
        }
      });
  }

  @Override
  protected abstract CloudSourceApplicationConfigurable<SC, DC, SR, AC> createApplicationConfigurable(@Nullable Project project,
                                                                                                      Disposable parentDisposable);

  protected abstract DC createDeploymentConfiguration();

  protected abstract void doConfigureModule(AC applicationConfiguration,
                                            DeployToServerRunConfiguration<SC, DC> runConfiguration,
                                            boolean firstAttempt,
                                            SR serverRuntime) throws ServerRuntimeException;
}
