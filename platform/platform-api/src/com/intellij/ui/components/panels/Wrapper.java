/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.components.panels;

import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.wm.IdeFocusManager;

import javax.swing.*;
import java.awt.*;

public class Wrapper extends JPanel implements NullableComponent {

  private JComponent myVerticalSizeReferent;
  private JComponent myHorizontalSizeReferent;

  public Wrapper() {
    setLayout(new BorderLayout());
    setOpaque(false);
  }

  public Wrapper(JComponent wrapped) {
    setLayout(new BorderLayout());
    add(wrapped, BorderLayout.CENTER);
    setOpaque(false);
  }

  public Wrapper(LayoutManager layout, JComponent wrapped) {
    super(layout);
    add(wrapped);
    setOpaque(false);
  }

  public Wrapper(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
    setOpaque(false);
  }

  public Wrapper(LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  public Wrapper(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
    setOpaque(false);
  }

  public void setContent(JComponent wrapped) {
    if (wrapped == getTargetComponent()) {
      return;
    }
    
    removeAll();
    setLayout(new BorderLayout());
    if (wrapped != null) {
      add(wrapped, BorderLayout.CENTER);
    }
    validate();
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

  public final Wrapper setVerticalSizeReferent(JComponent verticalSizeReferent) {
    myVerticalSizeReferent = verticalSizeReferent;
    return this;
  }

  public final Wrapper setHorizontalSizeReferent(JComponent horizontalSizeReferent) {
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

  public static class North extends Wrapper {
    public North(JComponent wrapped) {
      super(new BorderLayout());
      add(wrapped, BorderLayout.NORTH);
    }
  }
}
