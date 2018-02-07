/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.SourcePathsStep;
import com.intellij.ide.util.newProjectWizard.SupportForFrameworksStep;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectWizardStepFactoryImpl extends ProjectWizardStepFactory {
  private static final Key<ProjectJdkStep> PROJECT_JDK_STEP_KEY = Key.create("ProjectJdkStep");

  public ModuleWizardStep createNameAndLocationStep(final WizardContext wizardContext) {
    return new ProjectNameStep(wizardContext);
  }

  public ModuleWizardStep createSourcePathsStep(ModuleWizardStep nameAndLocationStep, SourcePathsBuilder builder, Icon icon, String helpId) {
    return null;
  }

  public ModuleWizardStep createSourcePathsStep(final WizardContext context, final SourcePathsBuilder builder, final Icon icon, @NonNls final String helpId) {
    return new SourcePathsStep(builder, icon, helpId);
  }

  /**
   * @deprecated
   */
  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               final String helpId) {
    return createProjectJdkStep(context, null, builder, isVisible, icon, helpId);
  }

  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               SdkType type,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               @NonNls final String helpId) {
    return new ProjectJdkForModuleStep(context, type){
      public void updateDataModel() {
        super.updateDataModel();
        builder.setModuleJdk(getJdk());
      }

      public boolean isStepVisible() {
        return isVisible.compute().booleanValue();
      }

      public Icon getIcon() {
        return icon;
      }

      @Override
      public String getName() {
        return "Specify JDK";
      }

      public String getHelpId() {
        return helpId;
      }
    };
  }

  public ModuleWizardStep createProjectJdkStep(final WizardContext wizardContext) {
    ProjectJdkStep projectSdkStep = wizardContext.getUserData(PROJECT_JDK_STEP_KEY);
    if (projectSdkStep != null) {
      return projectSdkStep;
    }
    projectSdkStep = new ProjectJdkStep(wizardContext) {
      public boolean isStepVisible() {
        final Sdk newProjectJdk = AbstractProjectWizard.getProjectSdkByDefault(wizardContext);
        if (newProjectJdk == null) return true;
        final ProjectBuilder projectBuilder = wizardContext.getProjectBuilder();
        return projectBuilder != null && !projectBuilder.isSuitableSdk(newProjectJdk);
      }
    };
    wizardContext.putUserData(PROJECT_JDK_STEP_KEY, projectSdkStep);
    return projectSdkStep;
  }

  @Nullable
  @Override
  public Sdk getNewProjectSdk(WizardContext wizardContext) {
    return AbstractProjectWizard.getNewProjectJdk(wizardContext);
  }

  @Override
  public ModuleWizardStep createSupportForFrameworksStep(WizardContext wizardContext, ModuleBuilder moduleBuilder) {
    return createSupportForFrameworksStep(wizardContext, moduleBuilder, ModulesProvider.EMPTY_MODULES_PROVIDER);
  }

  @Override
  public ModuleWizardStep createSupportForFrameworksStep(@NotNull WizardContext context, @NotNull ModuleBuilder builder, @NotNull ModulesProvider modulesProvider) {
    final LibrariesContainer container = LibrariesContainerFactory.createContainer(context, modulesProvider);
    return new SupportForFrameworksStep(context, builder, container);
  }

  @Override
  public ModuleWizardStep createJavaSettingsStep(@NotNull SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder, @NotNull Condition<SdkTypeId> sdkFilter) {
   return new JavaSettingsStep(settingsStep, moduleBuilder, sdkFilter);
  }
}
