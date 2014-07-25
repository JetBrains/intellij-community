/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
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

  public abstract void setName(String name);

  public abstract void setModuleFilePath(@NonNls String path);

  public abstract void setContentEntryPath(String moduleRootPath);

  @Override
  public boolean equals(Object obj) {
    return obj instanceof AbstractModuleBuilder && getBuilderId() != null && getBuilderId().equals(((AbstractModuleBuilder)obj).getBuilderId());
  }
}
