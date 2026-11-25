// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.options.CustomComponentExtension;
import com.intellij.codeInspection.options.OptCustom;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

/**
 * An extension for {@link OptCustom} control, which is capable to render itself in Swing UI.
 *
 * @param <T> type of custom data
 */
@ApiStatus.Experimental
public abstract class CustomComponentExtensionWithSwingRenderer<T> extends CustomComponentExtension<T> {
  protected CustomComponentExtensionWithSwingRenderer(@NotNull String id) {
    super(id);
  }

  /**
   * @param data        the data associated with the component, deserialized via {@link #deserializeData(String)}
   * @param controller  the controller for the current form
   * @param project     the context project to use, if necessary
   * @param refreshPane the callback that renders the whole options pane again with the current option values.
   *                    Call it on the EDT when the component changes data that the pane depends on.
   *                    For example, call it after a settings dialog adds an item that a drop-down must show.
   */
  public abstract @NotNull JComponent render(T data,
                                             @NotNull OptionController controller,
                                             @NotNull Project project,
                                             @NotNull Runnable refreshPane);
}
