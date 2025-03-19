// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.border.Border;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class DarculaInternalBorder implements UIResource, Border {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new InsetsUIResource(0, 0, 0, 0);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}
