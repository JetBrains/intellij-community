// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public interface WindowWrapper extends Disposable {
  enum Mode {FRAME, MODAL, NON_MODAL}

  void show();

  @Nullable
  Project getProject();

  @NotNull
  JComponent getComponent();

  @NotNull
  Mode getMode();

  @NotNull
  Window getWindow();

  default boolean isDisposed() {
    return false;
  }

  void setTitle(@Nullable String title);

  void setImages(@Nullable List<? extends Image> images);

  void close();
}
