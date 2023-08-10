// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

@Internal
public final class CornerAwareScrollPaneLayout extends JBScrollPane.Layout {

  private final @NotNull Component myCorner;

  public CornerAwareScrollPaneLayout(@NotNull Component corner) {
    myCorner = corner;
  }

  @Override
  public void layoutContainer(Container parent) {
    super.layoutContainer(parent);
    if (!myCorner.isVisible()) {
      return;
    }
    JScrollBar vsb = this.vsb;
    JScrollBar hsb = this.hsb;
    if (vsb == null && hsb == null) {
      return;
    }
    Rectangle cornerBounds = myCorner.getBounds();
    if (vsb != null) {
      vsb.setSize(vsb.getBounds().width, cornerBounds.y - 3);
    }
    if (hsb != null) {
      hsb.setSize(cornerBounds.x - 3, hsb.getBounds().height);
    }
  }
}
