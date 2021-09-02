// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.ui.WidthBasedLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

final class CombinedPopupLayout implements LayoutManager {

  private static final int MAX_POPUP_WIDTH = 650;

  private final @NotNull JComponent highlightInfoComponent;
  private final @NotNull DocumentationComponent quickDocComponent;

  CombinedPopupLayout(@NotNull JComponent highlightInfoComponent, @NotNull DocumentationComponent quickDocComponent) {
    this.highlightInfoComponent = highlightInfoComponent;
    this.quickDocComponent = quickDocComponent;
  }

  @Override
  public void addLayoutComponent(String name, Component comp) { }

  @Override
  public void removeLayoutComponent(Component comp) { }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    int w1 = WidthBasedLayout.getPreferredWidth(highlightInfoComponent);
    int w2 = WidthBasedLayout.getPreferredWidth(quickDocComponent);
    int preferredWidth = Math.min(JBUI.scale(MAX_POPUP_WIDTH), Math.max(w1, w2));
    int h1 = WidthBasedLayout.getPreferredHeight(highlightInfoComponent, preferredWidth);
    int h2 = WidthBasedLayout.getPreferredHeight(quickDocComponent, preferredWidth);
    return new Dimension(preferredWidth, h1 + h2);
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    Dimension d1 = highlightInfoComponent.getMinimumSize();
    Dimension d2 = quickDocComponent.getMinimumSize();
    return new Dimension(Math.max(d1.width, d2.width), d1.height + d2.height);
  }

  @Override
  public void layoutContainer(Container parent) {
    int width = parent.getWidth();
    int height = parent.getHeight();
    int h1 = Math.min(height, highlightInfoComponent.getPreferredSize().height);
    highlightInfoComponent.setBounds(0, 0, width, h1);
    quickDocComponent.setBounds(0, h1, width, height - h1);
  }
}
