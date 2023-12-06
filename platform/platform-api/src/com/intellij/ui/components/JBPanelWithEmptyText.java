// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author gregsh
 */
public class JBPanelWithEmptyText extends JBPanel<JBPanelWithEmptyText> implements ComponentWithEmptyText {
  private final StatusText emptyText = new StatusText(this) {
    @Override
    protected boolean isStatusVisible() {
      for (Component component : getComponents()) {
        if (component.isVisible()) {
          return false;
        }
      }
      return true;
    }
  };

  public JBPanelWithEmptyText() {
    super();
    registerEmptyTextComponents();
  }

  public JBPanelWithEmptyText(LayoutManager layout) {
    super(layout);
    registerEmptyTextComponents();
  }

  private void registerEmptyTextComponents() {
    putClientProperty(ComponentUtil.NOT_IN_HIERARCHY_COMPONENTS, emptyText.getWrappedFragmentsIterable());
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
}
