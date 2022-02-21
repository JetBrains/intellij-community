// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;

/**
 * @author gregsh
 */
public class JBPanelWithEmptyText extends JBPanel<JBPanelWithEmptyText> implements ComponentWithEmptyText {
  private final StatusText emptyText = new StatusText(this) {
    @Override
    protected boolean isStatusVisible() {
      //noinspection SSBasedInspection
      return Arrays.stream(getComponents()).noneMatch(Component::isVisible);
    }
  };

  public JBPanelWithEmptyText() {
    super();
  }

  public JBPanelWithEmptyText(LayoutManager layout) {
    super(layout);
  }

  @Override
  public @NotNull StatusText getEmptyText() {
    return emptyText;
  }

  public @NotNull JBPanelWithEmptyText withEmptyText(@Nls String str) {
    emptyText.setText(str);
    return this;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    emptyText.paint(this, g);
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }
}
