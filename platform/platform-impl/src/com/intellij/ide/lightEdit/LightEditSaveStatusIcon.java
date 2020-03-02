// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("SameParameterValue")
public class LightEditSaveStatusIcon extends ColorIcon {
  private final static int BASE_ICON_SIZE = 7;

  private LightEditSaveStatusIcon(@NotNull Color color) {
    super(BASE_ICON_SIZE, color);
  }

  @Override
  public void paintIcon(Component component, Graphics g, int i, int j) {
    final int iconWidth = getIconWidth();
    final int iconHeight = getIconHeight();
    g.setColor(getIconColor());

    final int size = Math.min(iconHeight, iconWidth);
    g.fillOval(i, j, size, size);
  }

  static Icon create(@NotNull Color color) {
    return JBUIScale.scaleIcon(new LightEditSaveStatusIcon(color));
  }

}
