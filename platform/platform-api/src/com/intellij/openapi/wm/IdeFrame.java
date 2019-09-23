// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.ui.BalloonLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface IdeFrame {
  DataKey<IdeFrame> KEY = DataKey.create("IdeFrame");

  @Nullable
  StatusBar getStatusBar();

  @NotNull
  Rectangle suggestChildFrameBounds();

  @Nullable
  Project getProject();

  void setFrameTitle(String title);

  JComponent getComponent();

  @Nullable
  BalloonLayout getBalloonLayout();

  default boolean isInFullScreen() {
    return false;
  }

  interface Child extends IdeFrame {
  }
}