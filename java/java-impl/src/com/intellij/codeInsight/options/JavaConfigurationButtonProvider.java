// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.options;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;

@ApiStatus.Internal
public class JavaConfigurationButtonProvider {
  static @NotNull JavaConfigurationButtonProvider getInstance() {
    return ApplicationManager.getApplication().getService(JavaConfigurationButtonProvider.class);
  }

  /**
   * @param info    button kind info
   * @return an {@link OptRegularComponent} component that represents the button to show a configuration dialog.
   * May return an empty control if no configuration dialog is supported by the provider.
   */
  public @NotNull OptRegularComponent button(@NotNull JavaConfigurationDialogInfo info) {
    // Empty
    return OptPane.horizontalStack();
  }

  /**
   * @param kind button kind
   * @return true if the dialog can be shown for the given configuration kind
   */
  public boolean isConfigurationUISupported(@NotNull JavaConfigurationDialogInfo kind) {
    return false;
  }

  /**
   * Shows the configuration dialog for the given configuration kind if supported. Do nothing if not supported.
   *
   * @param info button kind info
   */
  public void showConfigurationDialog(@NotNull JavaConfigurationDialogInfo info,
                                      @Nullable Component parent,
                                      @NotNull Project project) {
  }
}
