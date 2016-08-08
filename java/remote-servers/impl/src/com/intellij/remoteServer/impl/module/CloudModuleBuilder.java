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

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilderListener;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;


public class CloudModuleBuilder extends JavaModuleBuilder {

  private RemoteServer<?> myAccount;
  private CloudApplicationConfiguration myApplicationConfiguration;
  private FrameworkSupportModelBase myFrameworkSupportModel;

  private Map<ServerType<?>, CloudModuleBuilderContribution> myCloudType2Contribution;
  private Project myProject;

  public CloudModuleBuilder() {
    myCloudType2Contribution = new HashMap<>();

    ModuleConfigurationUpdater configurationUpdater = new ModuleConfigurationUpdater() {

      public void update(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel) {
        preConfigureModule(module, rootModel);
      }
    };
    addModuleConfigurationUpdater(configurationUpdater);

    addListener(new ModuleBuilderListener() {

      @Override
      public void moduleCreated(@NotNull Module module) {
        configureModule(module);
      }
    });
  }

  public String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public Icon getBigIcon() {
    return AllIcons.General.Balloon;
  }

  @Override
  public Icon getNodeIcon() {
    return AllIcons.General.Balloon;
  }

  @Override
  public String getDescription() {
    return "Java module of PAAS cloud application";
  }

  @Override
  public String getPresentableName() {
    return "Clouds";
  }

  @Override
  public String getGroupName() {
    return "Clouds";
  }

  @Override
  public String getParentGroup() {
    return JavaModuleType.JAVA_GROUP;
  }

  @Override
  public int getWeight() {
    return 30;
  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Nullable
  @Override
  public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
    myProject = context.getProject();
    return new CloudModuleWizardStep(this, myProject, parentDisposable);
  }

  public void setAccount(RemoteServer<?> account) {
    myAccount = account;
  }

  public RemoteServer<?> getAccount() {
    return myAccount;
  }

  public void setApplicationConfiguration(CloudApplicationConfiguration applicationConfiguration) {
    myApplicationConfiguration = applicationConfiguration;
  }

  public CloudApplicationConfiguration getApplicationConfiguration() {
    return myApplicationConfiguration;
  }

  public CloudModuleBuilderContribution getContribution(ServerType<?> cloudType) {
    CloudModuleBuilderContribution result = myCloudType2Contribution.get(cloudType);
    if (result == null) {
      result = CloudModuleBuilderContributionFactory.getInstanceByType(cloudType).createContribution(this);
      myCloudType2Contribution.put(cloudType, result);
    }
    return result;
  }

  private CloudModuleBuilderContribution getContribution() {
    return getContribution(myAccount.getType());
  }

  private void preConfigureModule(Module module, ModifiableRootModel model) {
    getContribution().preConfigureModule(module, model);
  }

  private void configureModule(final Module module) {
    getContribution().configureModule(module);
  }

  public FrameworkSupportModelBase getFrameworkSupportModel() {
    if (myFrameworkSupportModel == null) {
      final LibrariesContainer librariesContainer = LibrariesContainerFactory.createContainer(myProject);
      myFrameworkSupportModel = new FrameworkSupportModelBase(myProject, this, librariesContainer) {

        @NotNull
        @Override
        public String getBaseDirectoryForLibrariesPath() {
          return StringUtil.notNullize(getContentEntryPath());
        }
      };
    }
    return myFrameworkSupportModel;
  }

  @Override
  protected boolean isAvailable() {
    return CloudModuleBuilderContributionFactory.EP_NAME.getExtensions().length > 0;
  }
}
