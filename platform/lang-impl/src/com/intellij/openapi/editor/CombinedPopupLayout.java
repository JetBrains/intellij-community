// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.ui.WidthBasedLayout;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class CombinedPopupLayout implements LayoutManager {

  private static final int MAX_POPUP_WIDTH = 950;

  private final @Nullable JComponent highlightInfoComponent;
  private final @Nullable JComponent quickDocComponent;

  CombinedPopupLayout(@Nullable JComponent highlightInfoComponent, @Nullable JComponent quickDocComponent) {
    this.highlightInfoComponent = highlightInfoComponent;
    this.quickDocComponent = quickDocComponent;
  }

  @Override
  public void addLayoutComponent(String name, Component comp) { }

  @Override
  public void removeLayoutComponent(Component comp) { }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    // QuickDoc contents can be updated,
    // we need to clear the preferred size cache
    if (quickDocComponent != null) {
      quickDocComponent.setPreferredSize(null);
    }
    int w1 = WidthBasedLayout.getPreferredWidth(highlightInfoComponent);
    int w2 = WidthBasedLayout.getPreferredWidth(quickDocComponent);
    int preferredWidth = Math.min(JBUI.scale(MAX_POPUP_WIDTH), Math.max(w1, w2));
    int h1 = WidthBasedLayout.getPreferredHeight(highlightInfoComponent, preferredWidth);
    int h2 = WidthBasedLayout.getPreferredHeight(quickDocComponent, preferredWidth);
    return new Dimension(preferredWidth, h1 + h2);
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    Dimension d1 = highlightInfoComponent == null ? new Dimension() : highlightInfoComponent.getMinimumSize();
    Dimension d2 = quickDocComponent == null ? new Dimension() : quickDocComponent.getMinimumSize();
    return new Dimension(Math.max(d1.width, d2.width), d1.height + d2.height);
  }

  @Override
  public void layoutContainer(Container parent) {
    int width = parent.getWidth();
    int height = parent.getHeight();
    if (highlightInfoComponent == null) {
      if (quickDocComponent != null) quickDocComponent.setBounds(0, 0, width, height);
    }
    else if (quickDocComponent == null) {
      highlightInfoComponent.setBounds(0, 0, width, height);
    }
    else {
      int h1 = WidthBasedLayout.getPreferredHeight(highlightInfoComponent, width);
      int h2 = WidthBasedLayout.getPreferredHeight(quickDocComponent, width);

      int h1Res;
      int h2Res;
      if (h1 + h2 <= height) {
        h1Res = h1;
        h2Res = height - h1;
      }
      else if (h1 < height / 2) {
        h1Res = h1;
        h2Res = height - h1;
      }
      else if (h2 < height / 2) {
        h1Res = height - h2;
        h2Res = h2;
      }
      else {
        double h1Fraction = ((double) h1) / (h1 + h2);
        h1Res = (int) (h1Fraction * height);
        h2Res = height - h1Res;
      }

      highlightInfoComponent.setBounds(0, 0, width, h1Res);
      quickDocComponent.setBounds(0, h1Res, width, h2Res);
    }
  }
}
