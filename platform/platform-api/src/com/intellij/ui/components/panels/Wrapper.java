// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components.panels;

import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class Wrapper extends JPanel implements NullableComponent {
  private JComponent myVerticalSizeReferent;
  private JComponent myHorizontalSizeReferent;

  public Wrapper() {
    setLayout(new BorderLayout());
    setOpaque(false);
  }

  public Wrapper(@Nullable JComponent wrapped) {
    setLayout(new BorderLayout());
    if (wrapped != null) {
      add(wrapped, BorderLayout.CENTER);
    }
    setOpaque(false);
  }

  /**
   * WARNING: the layout will be overwritten by {@link #setContent}
   */
  public Wrapper(LayoutManager layout, @Nullable JComponent wrapped) {
    super(layout);
    if (wrapped != null) {
      add(wrapped);
    }
    setOpaque(false);
  }

  public Wrapper(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
    setOpaque(false);
  }

  /**
   * WARNING: the layout will be overwritten by {@link #setContent}
   */
  public Wrapper(LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  /**
   * WARNING: the layout will be overwritten by {@link #setContent}
   */
  public Wrapper(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
    setOpaque(false);
  }

  public void setContent(@Nullable JComponent wrapped) {
    if (wrapped == getTargetComponent()) {
      return;
    }

    removeAll();
    setLayout(new BorderLayout());
    if (wrapped != null) {
      add(wrapped, BorderLayout.CENTER);
    }
    revalidate();
    repaint();
  }

  @Override
  public boolean isNull() {
    return getComponentCount() == 0;
  }

  @Override
  public void requestFocus() {
    if (getTargetComponent() == this) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> super.requestFocus());
      return;
    }
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(getTargetComponent(), true));
  }

  @Override
  public boolean requestFocusInWindow() {
    if (getTargetComponent() == this) {
      return super.requestFocusInWindow();
    }
    return getTargetComponent().requestFocusInWindow();
  }

  public void requestFocusInternal() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> super.requestFocus());
  }

  @Override
  public final boolean requestFocus(boolean temporary) {
    if (getTargetComponent() == this) {
      return super.requestFocus(temporary);
    }
    return getTargetComponent().requestFocus(temporary);
  }

  public JComponent getTargetComponent() {
    if (getComponentCount() == 1) {
      return (JComponent) getComponent(0);
    } else {
      return this;
    }
  }

  public final Wrapper setVerticalSizeReferent(@Nullable JComponent verticalSizeReferent) {
    myVerticalSizeReferent = verticalSizeReferent;
    return this;
  }

  public final Wrapper setHorizontalSizeReferent(@Nullable JComponent horizontalSizeReferent) {
    myHorizontalSizeReferent = horizontalSizeReferent;
    return this;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    if (myHorizontalSizeReferent != null && myHorizontalSizeReferent.isShowing()) {
      size.width = Math.max(size.width, myHorizontalSizeReferent.getPreferredSize().width);
    }
    if (myVerticalSizeReferent != null && myVerticalSizeReferent.isShowing()) {
      size.height = Math.max(size.height, myVerticalSizeReferent.getPreferredSize().height);
    }
    return size;
  }

  /**
   * WARNING: the layout will be overwritten by {@link #setContent}
   */
  public static final class North extends Wrapper {
    public North(JComponent wrapped) {
      super(new BorderLayout());
      add(wrapped, BorderLayout.NORTH);
    }
  }
}
