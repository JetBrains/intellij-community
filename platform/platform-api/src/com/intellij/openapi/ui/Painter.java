// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public interface Painter {
  boolean needsRepaint();

  void paint(Component component, final Graphics2D g);

  void addListener(@NotNull Listener listener);

  void removeListener(Listener listener);

  interface Listener {
    void onNeedsRepaint(@NotNull Painter painter, @Nullable JComponent dirtyComponent);
  }
}
