/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

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
      super.requestFocus();
      return;
    }
    getTargetComponent().requestFocus();
  }

  @Override
  public boolean requestFocusInWindow() {
    if (getTargetComponent() == this) {
      return super.requestFocusInWindow();
    }
    return getTargetComponent().requestFocusInWindow();
  }

  public void requestFocusInternal() {
    super.requestFocus();
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

  public static class FocusHolder extends Wrapper implements FocusListener {

    private Runnable myFocusGainedCallback;

    public FocusHolder() {
      init();
    }

    public FocusHolder(final JComponent wrapped) {
      super(wrapped);
      init();
    }

    public FocusHolder(final LayoutManager layout, final JComponent wrapped) {
      super(layout, wrapped);
      init();
    }

    public FocusHolder(final boolean isDoubleBuffered) {
      super(isDoubleBuffered);
      init();
    }

    public FocusHolder(final LayoutManager layout) {
      super(layout);
      init();
    }

    public FocusHolder(final LayoutManager layout, final boolean isDoubleBuffered) {
      super(layout, isDoubleBuffered);
      init();
    }

    private void init() {
      UIUtil.setFocusProxy(this, true);
      setFocusable(true);
      addFocusListener(this);
    }

    public void requestFocus(Runnable callback) {
      myFocusGainedCallback = callback;
      if (isFocusOwner()) {
        processCallback();    
      } else {
        requestFocusInternal();
      }
    }

    @Override
    public void focusGained(final FocusEvent e) {
      processCallback();
    }

    private void processCallback() {
      if (myFocusGainedCallback != null) {
        Runnable callback = myFocusGainedCallback;
        myFocusGainedCallback = null;
        callback.run();
      }
    }

    @Override
    public void focusLost(final FocusEvent e) {
    }

  }

  public static class North extends Wrapper {
    public North(JComponent wrapped) {
      super(new BorderLayout());
      add(wrapped, BorderLayout.NORTH);
    }
  }
}
