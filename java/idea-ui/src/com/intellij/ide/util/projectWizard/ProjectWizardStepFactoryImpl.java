// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.newProjectWizard.SourcePathsStep;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public final class ProjectWizardStepFactoryImpl extends ProjectWizardStepFactory {
  private static final Key<ProjectJdkStep> PROJECT_JDK_STEP_KEY = Key.create("ProjectJdkStep");

  @Override
  public ModuleWizardStep createNameAndLocationStep(final WizardContext wizardContext) {
    return new ProjectNameStep(wizardContext);
  }

  @Override
  public ModuleWizardStep createSourcePathsStep(final WizardContext context, final SourcePathsBuilder builder, final Icon icon, @NonNls final String helpId) {
    return new SourcePathsStep(builder, icon, helpId);
  }

  @Override
  public ModuleWizardStep createProjectJdkStep(WizardContext context,
                                               SdkType type,
                                               final JavaModuleBuilder builder,
                                               final Computable<Boolean> isVisible,
                                               final Icon icon,
                                               @NonNls final String helpId) {
    return new ProjectJdkForModuleStep(context, type, helpId) {
      @Override
      public void updateDataModel() {
        super.updateDataModel();
        builder.setModuleJdk(getJdk());
      }

      @Override
      public boolean isStepVisible() {
        return isVisible.compute().booleanValue();
      }

      @Override
      public Icon getIcon() {
        return icon;
      }

      @Override
      public String getName() {
        return "Specify JDK";
      }
    };
  }

  @Override
  public ModuleWizardStep createProjectJdkStep(final WizardContext wizardContext) {
    ProjectJdkStep projectSdkStep = wizardContext.getUserData(PROJECT_JDK_STEP_KEY);
    if (projectSdkStep != null) {
      return projectSdkStep;
    }
    projectSdkStep = new ProjectJdkStep(wizardContext) {
      @Override
      public boolean isStepVisible() {
        final Sdk newProjectJdk = AbstractProjectWizard.getProjectSdkByDefault(wizardContext);
        if (newProjectJdk == null) return true;
        final ProjectBuilder projectBuilder = wizardContext.getProjectBuilder();
        return projectBuilder != null && !projectBuilder.isSuitableSdkType(newProjectJdk.getSdkType());
      }
    };
    wizardContext.putUserData(PROJECT_JDK_STEP_KEY, projectSdkStep);
    return projectSdkStep;
  }

  @Override
  public ModuleWizardStep createJavaSettingsStep(@NotNull SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder, @NotNull Condition<? super SdkTypeId> sdkFilter) {
   return new JavaSettingsStep(settingsStep, moduleBuilder, sdkFilter);
  }
}
