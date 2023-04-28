// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.options.CustomComponentExtension;
import com.intellij.codeInspection.options.OptCustom;
import com.intellij.codeInspection.options.OptionController;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Experimental
public abstract class CustomComponentExtensionWithSwingRenderer<T> extends CustomComponentExtension<T> {
  protected CustomComponentExtensionWithSwingRenderer(@NotNull String id) {
    super(id);
  }

  /**
   * Render the custom component
   * 
   * @param control original control
   * @param parent parent Swing component
   * @param controller context option controller
   * @return the rendered JComponent
   */
  public final @NotNull JComponent render(@NotNull OptCustom control, @Nullable Component parent, @NotNull OptionController controller) {
    return render(deserializeData(control.data()), parent, controller);
  }

  /**
   * Render the custom component
   *
   * @param data       component data
   * @param parent     parent Swing component
   * @param controller context option controller
   * @return the rendered JComponent
   */
  abstract public @NotNull JComponent render(T data, @Nullable Component parent, @NotNull OptionController controller);
}
