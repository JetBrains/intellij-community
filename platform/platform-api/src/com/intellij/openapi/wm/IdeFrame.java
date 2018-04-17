// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.ui.BalloonLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public interface IdeFrame {
  DataKey<IdeFrame> KEY = DataKey.create("IdeFrame");

  StatusBar getStatusBar();

  Rectangle suggestChildFrameBounds();

  @Nullable
  Project getProject();

  void setFrameTitle(String title);
  void setFileTitle(String fileTitle, File ioFile);

  IdeRootPaneNorthExtension getNorthExtension(String key);

  JComponent getComponent();

  @Nullable
  BalloonLayout getBalloonLayout();

  interface Child extends IdeFrame { }
}