// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.options.CustomComponentExtension;
import com.intellij.codeInspection.options.OptCustom;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Experimental
public abstract class CustomComponentExtensionWithSwingRenderer<T> extends CustomComponentExtension<T> {
  protected CustomComponentExtensionWithSwingRenderer(@NotNull String id) {
    super(id);
  }
  
  public final @NotNull JComponent render(@NotNull OptCustom control, @NotNull Project project) {
    return render(deserializeData(control.data()), project);
  }
  
  public abstract @NotNull JComponent render(T data, @NotNull Project project);
}
