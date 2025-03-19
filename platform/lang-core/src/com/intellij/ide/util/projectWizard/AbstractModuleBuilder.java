// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public abstract class AbstractModuleBuilder extends ProjectBuilder {
  public abstract Icon getNodeIcon();

  public abstract @Nullable @NonNls String getBuilderId();

  public abstract ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider);

  public ModuleWizardStep[] createFinishingSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  public @Nullable ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return null;
  }

  public @Nullable ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep step) { return null; }

  /**
   * Custom UI to be shown on the first wizard page
   */
  public @Nullable ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
    return null;
  }

  public boolean validateModuleName(@NotNull String moduleName) throws ConfigurationException {
    return true;
  }

  public abstract void setName(String name);

  public abstract void setModuleFilePath(@NonNls String path);

  public abstract void setContentEntryPath(String moduleRootPath);

  @Override
  public boolean equals(Object obj) {
    return obj instanceof AbstractModuleBuilder && getBuilderId() != null && getBuilderId().equals(((AbstractModuleBuilder)obj).getBuilderId());
  }
}
