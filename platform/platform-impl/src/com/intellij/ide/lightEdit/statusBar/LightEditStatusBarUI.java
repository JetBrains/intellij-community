// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.statusBar;

import com.intellij.openapi.wm.impl.status.StatusBarUI;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class LightEditStatusBarUI extends StatusBarUI {

  private static final int HEIGHT = 26;

  @Override
  public @NotNull Color getBackground() {
    return JBColor.namedColor("StatusBar.LightEditBackground", new JBColor(0xe3eefc, 0x2f475e));
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return withHeight(super.getMinimumSize(c));
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return withHeight(super.getMaximumSize(c));
  }

  public static @NotNull Dimension withHeight(@NotNull Dimension size) {
    return new Dimension(size.width, JBUI.scale(HEIGHT));
  }
}
