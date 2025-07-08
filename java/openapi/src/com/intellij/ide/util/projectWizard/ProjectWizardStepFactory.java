// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A factory for creating some commonly used project wizards steps
 */
public abstract class ProjectWizardStepFactory {

  public static ProjectWizardStepFactory getInstance() {
    return ApplicationManager.getApplication().getService(ProjectWizardStepFactory.class);
  }

  /**
   * @deprecated the current implementation of the 'New Project' wizard doesn't have a separate step for specifying project name and location.
   */
  @Deprecated
  public abstract ModuleWizardStep createNameAndLocationStep(WizardContext wizardContext);

  /**
   * @deprecated the current implementation of the 'New Project' wizard doesn't have a separate step for selecting source paths.
   */
  @Deprecated
  public abstract ModuleWizardStep createSourcePathsStep(WizardContext context, SourcePathsBuilder builder, Icon icon, @NonNls String helpId);

  /**
   * @deprecated the current implementation of the 'New Project' wizard doesn't have a separate step for SDK selection.
   */
  @Deprecated
  public abstract ModuleWizardStep createProjectJdkStep(WizardContext context, SdkType type, JavaModuleBuilder builder, Computable<Boolean> isVisible, Icon icon, @NonNls String helpId);

  public abstract ModuleWizardStep createProjectJdkStep(final WizardContext wizardContext);

  public abstract ModuleWizardStep createJavaSettingsStep(@NotNull SettingsStep settingsStep, @NotNull ModuleBuilder moduleBuilder, @NotNull Condition<? super SdkTypeId> sdkFilter);
}
