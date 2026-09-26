// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

public enum JavaConfigurationDialogKind {
  NULLABILITY_ANNOTATIONS,
  NULLABILITY_ANNOTATIONS_INSTRUMENTATION,
  ENTRY_POINT_CODE_PATTERNS,
  /**
   * Entry point annotations + implicit write annotations
   */
  ENTRY_POINT_ANNOTATIONS,
  /**
   * Implicit write annotations only
   */
  IMPLICIT_WRITE_ANNOTATIONS,
  DEPENDENCY_CONFIGURATION;

  /**
   * @return an {@link OptRegularComponent} component that represents the button to show a configuration dialog.
   * May return an empty control if no configuration dialog is supported by the provider.
   */
  public OptRegularComponent button() {
    return JavaInspectionButtonProvider.getInstance().button(this);
  }

  /**
   * @return true if configuration dialog is supported for this kind of configuration.
   */
  public boolean isConfigurationUISupported() {
    return JavaInspectionButtonProvider.getInstance().isConfigurationUISupported(this);
  }

  /**
   * Shows configuration dialog for this kind of configuration.
   * 
   * @param parent parent component for the dialog
   * @param project project for which the configuration is shown
   */
  public void showConfigurationDialog(@Nullable Component parent, @NotNull Project project) {
    JavaInspectionButtonProvider.getInstance().showConfigurationDialog(this, parent, project);
  }
}
