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

import com.intellij.ui.AnchorableComponent;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class JBLabel extends JLabel implements AnchorableComponent {
  private UIUtil.ComponentStyle myComponentStyle = UIUtil.ComponentStyle.REGULAR;
  private UIUtil.FontColor myFontColor = UIUtil.FontColor.NORMAL;
  private JComponent myAnchor = null;

  public JBLabel() {
    super();
  }

  public JBLabel(@NotNull UIUtil.ComponentStyle componentStyle) {
    super();
    setComponentStyle(componentStyle);
  }

  public JBLabel(@Nullable Icon image) {
    super(image);
  }

  public JBLabel(@NotNull String text) {
    super(text);
  }

  public JBLabel(@NotNull String text, @NotNull UIUtil.ComponentStyle componentStyle) {
    super(text);
    setComponentStyle(componentStyle);
  }

  public JBLabel(@NotNull String text, @NotNull UIUtil.ComponentStyle componentStyle, @NotNull UIUtil.FontColor fontColor) {
    super(text);
    setComponentStyle(componentStyle);
    setFontColor(fontColor);
  }

  public JBLabel(@NotNull String text, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, horizontalAlignment);
  }

  public JBLabel(@Nullable Icon image, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(image, horizontalAlignment);
  }

  public JBLabel(@NotNull String text, @Nullable Icon icon, @JdkConstants.HorizontalAlignment int horizontalAlignment) {
    super(text, icon, horizontalAlignment);
  }

  public void setComponentStyle(@NotNull UIUtil.ComponentStyle componentStyle) {
    myComponentStyle = componentStyle;
    UIUtil.applyStyle(componentStyle, this);
  }

  public UIUtil.ComponentStyle getComponentStyle() {
    return myComponentStyle;
  }

  public UIUtil.FontColor getFontColor() {
    return myFontColor;
  }

  public void setFontColor(@NotNull UIUtil.FontColor fontColor) {
    myFontColor = fontColor;
  }

  @Override
  public Color getForeground() {
    if (!isEnabled()) {
      return UIUtil.getLabelDisabledForeground();
    }
    if (myFontColor != null) {
      return UIUtil.getLabelFontColor(myFontColor);
    }
    return super.getForeground();
  }

  @Override
  public void setForeground(Color fg) {
    myFontColor = null;
    super.setForeground(fg);
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    myAnchor = anchor;
  }

  @Override
  public JComponent getAnchor() {
    return myAnchor;
  }

  @Override
  public Dimension getPreferredSize() {
    return myAnchor == null || myAnchor == this ? super.getPreferredSize() : myAnchor.getPreferredSize();
  }

  @Override
  public Dimension getMinimumSize() {
    return myAnchor == null || myAnchor == this ? super.getMinimumSize() : myAnchor.getMinimumSize();
  }
}
