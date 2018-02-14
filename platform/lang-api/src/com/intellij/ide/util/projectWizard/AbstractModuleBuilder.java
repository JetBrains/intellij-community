// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class AbstractModuleBuilder extends ProjectBuilder {
  public abstract Icon getNodeIcon();

  @Nullable
  public abstract String getBuilderId();

  public abstract ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider);

  public ModuleWizardStep[] createFinishingSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    return ModuleWizardStep.EMPTY_ARRAY;
  }

  @Nullable
  public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
    return null;
  }

  @Nullable
  public ModuleWizardStep modifyProjectTypeStep(@NotNull SettingsStep step) { return null; }

  /**
   * Custom UI to be shown on the first wizard page
   */
  @Nullable
  public ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
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
