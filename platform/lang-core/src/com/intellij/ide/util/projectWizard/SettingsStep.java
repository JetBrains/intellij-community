// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public interface SettingsStep {

  WizardContext getContext();

  void addSettingsField(@NlsContexts.Label @NotNull String label, @NotNull JComponent field);

  void addSettingsComponent(@NotNull JComponent component);

  void addExpertPanel(@NotNull JComponent panel);
  void addExpertField(@NlsContexts.Label @NotNull String label, @NotNull JComponent field);

  /**
   * @deprecated use {@link #getModuleNameLocationSettings()} instead
   */
  @Deprecated
  default @Nullable JTextField getModuleNameField() {
    return null;
  }

  default @Nullable ModuleNameLocationSettings getModuleNameLocationSettings() {
    return null;
  }
}
