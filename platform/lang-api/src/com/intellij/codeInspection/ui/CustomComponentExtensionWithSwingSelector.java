// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.options.OptCustom;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A kind of custom component that can display its own Swing UI (dialog box) to set its options.
 * {@link OptPaneUtils#editOptions} calls it instead of the options dialog if the custom component is the only one in the pane.
 */
@ApiStatus.Experimental
public abstract class CustomComponentExtensionWithSwingSelector<T> extends CustomComponentExtensionWithSwingRenderer<T> {
  protected CustomComponentExtensionWithSwingSelector(@NotNull String id) {
    super(id);
  }

  public final boolean select(@NotNull OptCustom control, @NotNull OptionController controller, @NotNull Project project) {
    return select(deserializeData(control.data()), controller, project);
  }

  /**
   * Displays Swing UI (dialog) and sets the options through {@code controller}.
   *
   * @return false if the user dismisses the UI
   */
  public abstract boolean select(T data, @NotNull OptionController controller, @NotNull Project project);
}
