// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

final class CornerAwareScrollPaneLayout extends JBScrollPane.Layout {

  private final @NotNull Component myCorner;

  CornerAwareScrollPaneLayout(@NotNull Component corner) {
    myCorner = corner;
  }

  @Override
  public void layoutContainer(Container parent) {
    super.layoutContainer(parent);
    if (!myCorner.isVisible()) {
      return;
    }
    if (vsb != null) {
      Rectangle bounds = vsb.getBounds();
      vsb.setBounds(bounds.x, bounds.y, bounds.width, bounds.height - myCorner.getPreferredSize().height - 3);
    }
    if (hsb != null) {
      Rectangle bounds = hsb.getBounds();
      int vsbOffset = vsb != null ? vsb.getBounds().width : 0;
      hsb.setBounds(bounds.x, bounds.y, bounds.width - myCorner.getPreferredSize().width - 3 + vsbOffset, bounds.height);
    }
  }
}
