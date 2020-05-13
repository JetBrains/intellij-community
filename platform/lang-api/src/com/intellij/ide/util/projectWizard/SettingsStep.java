// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public interface SettingsStep {

  WizardContext getContext();

  void addSettingsField(@Nls @NotNull String label, @NotNull JComponent field);

  void addSettingsComponent(@NotNull JComponent component);

  void addExpertPanel(@NotNull JComponent panel);
  void addExpertField(@NotNull String label, @NotNull JComponent field);

  /**
   * @deprecated use {@link #getModuleNameLocationSettings()} instead
   */
  @Deprecated
  @Nullable
  JTextField getModuleNameField();

  @Nullable
  default ModuleNameLocationSettings getModuleNameLocationSettings() {
    return null;
  }
}
