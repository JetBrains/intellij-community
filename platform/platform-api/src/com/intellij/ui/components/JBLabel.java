/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JBLabel extends JLabel {
  private Style myStyle = Style.REGULAR;

  public enum Style {REGULAR, SMALL}

  public JBLabel() {
  }

  public JBLabel(@NotNull Style style) {
    setStyle(style);
  }

  public JBLabel(@Nullable Icon image) {
    super(image);
  }

  public JBLabel(@NotNull String text) {
    super(text);
  }

  public JBLabel(@NotNull String text, @NotNull Style style) {
    super(text);
    setStyle(style);
  }

  public JBLabel(@NotNull String text, int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  public JBLabel(@Nullable Icon image, int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  public JBLabel(@NotNull String text, @Nullable Icon icon, int horizontalAlignment) {
    super(text, icon, horizontalAlignment);
  }

  public void setStyle(@NotNull Style style) {
    myStyle = style;

    if (SystemInfo.isMac) {
      putClientProperty("JComponent.sizeVariant", style == Style.REGULAR ? "regular" : "small");
    }
    else {
      setFont(UIUtil.getFont(style == Style.SMALL ? UIUtil.FontSize.SMALL : UIUtil.FontSize.NORMAL, getFont()));
    }
    Container p = getParent();
    if (p != null) {
      SwingUtilities.updateComponentTreeUI(p);
    }
  }

  public Style getStyle() {
    return myStyle;
  }
}
