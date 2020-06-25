// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.remoteServer.CloudBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;


public class CloudModuleBuilder extends JavaModuleBuilder {

  private RemoteServer<?> myAccount;
  private CloudApplicationConfiguration myApplicationConfiguration;
  private FrameworkSupportModelBase myFrameworkSupportModel;

  private final Map<ServerType<?>, CloudModuleBuilderContribution> myCloudType2Contribution;
  private Project myProject;

  public CloudModuleBuilder() {
    myCloudType2Contribution = new HashMap<>();

    ModuleConfigurationUpdater configurationUpdater = new ModuleConfigurationUpdater() {

      @Override
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

  @Override
  public String getBuilderId() {
    return getClass().getName();
  }

  @Override
  public Icon getNodeIcon() {
    return AllIcons.General.Balloon;
  }

  @Override
  public String getDescription() {
    return CloudBundle.message("module.builder.description.java.module.of.paas.cloud.application");
  }

  @Override
  public String getPresentableName() {
    return CloudBundle.message("presentable.name.clouds");
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
  public boolean isAvailable() {
    return CloudModuleBuilderContributionFactory.EP_NAME.getExtensions().length > 0;
  }
}
