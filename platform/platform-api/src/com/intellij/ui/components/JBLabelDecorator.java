// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author evgeny.zakrevsky
 */
public final class JBLabelDecorator extends JBLabel {
  private JBLabelDecorator() {
    super();
  }

  private JBLabelDecorator(@Nullable Icon image) {
    super(image);
  }

  private JBLabelDecorator(@NotNull String text) {
    super(text);
  }

  private JBLabelDecorator(@NotNull String text, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  private JBLabelDecorator(@Nullable Icon image, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  private JBLabelDecorator(@NotNull String text, @Nullable Icon icon, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, icon, horizontalAlignment);
  }

  public static JBLabelDecorator createJBLabelDecorator() {
    return new JBLabelDecorator();
  }

  public static JBLabelDecorator createJBLabelDecorator(String text) {
    return new JBLabelDecorator(text);
  }

  public JBLabelDecorator setBold(boolean isBold) {
    if (isBold) {
      setFont(getFont().deriveFont(Font.BOLD));
    } else {
      setFont(getFont().deriveFont(Font.PLAIN));
    }
    return this;
  }

  public JBLabelDecorator setComponentStyleDecorative(@NotNull UIUtil.ComponentStyle componentStyle) {
    super.setComponentStyle(componentStyle);
    return this;
  }

  public JBLabelDecorator setFontColorDecorative(@NotNull UIUtil.FontColor fontColor) {
    super.setFontColor(fontColor);
    return this;
  }
}
