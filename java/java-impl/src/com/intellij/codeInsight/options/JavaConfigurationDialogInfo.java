// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

@NotNullByDefault
public record JavaConfigurationDialogInfo(JavaConfigurationDialogKind kind, Language language) {
  /**
   * @return an {@link OptRegularComponent} component that represents the button to show a configuration dialog.
   * May return an empty control if no configuration dialog is supported by the provider.
   */
  public OptRegularComponent button() {
    return JavaConfigurationButtonProvider.getInstance().button(this);
  }

  /**
   * @return true if the configuration dialog is supported for this configuration info
   */
  public boolean isConfigurationUISupported() {
    return JavaConfigurationButtonProvider.getInstance().isConfigurationUISupported(this);
  }

  /**
   * Shows configuration dialog for this configuration info.
   *
   * @param parent parent component for the dialog
   * @param project project for which the configuration is shown
   */
  public void showConfigurationDialog(@Nullable Component parent, Project project) {
    JavaConfigurationButtonProvider.getInstance().showConfigurationDialog(this, parent, project);
  }
}
