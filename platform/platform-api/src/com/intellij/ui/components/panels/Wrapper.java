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

  public Wrapper() {
    setLayout(new BorderLayout());
  }

  public Wrapper(JComponent wrapped) {
    setLayout(new BorderLayout());
    add(wrapped, BorderLayout.CENTER);
  }

  public Wrapper(LayoutManager layout, JComponent wrapped) {
    super(layout);
    add(wrapped);
  }

  public Wrapper(boolean isDoubleBuffered) {
    super(isDoubleBuffered);
  }

  public Wrapper(LayoutManager layout) {
    super(layout);
  }

  public Wrapper(LayoutManager layout, boolean isDoubleBuffered) {
    super(layout, isDoubleBuffered);
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

  public boolean isNull() {
    return getComponentCount() == 0;
  }

  public void requestFocus() {
    if (getTargetComponent() == this) {
      super.requestFocus();
      return;
    }
    getTargetComponent().requestFocus();
  }

  public boolean requestFocusInWindow() {
    if (getTargetComponent() == this) {
      return super.requestFocusInWindow();
    }
    return getTargetComponent().requestFocusInWindow();
  }

  public void requestFocusInternal() {
    super.requestFocus();
  }

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
