// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.ui;

import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public abstract class VisibilityPanelBase<V> extends JPanel {
  private final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  public abstract @Nullable V getVisibility();

  public abstract void setVisibility(V visibility);

  public void addListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }

  protected void stateChanged(ChangeEvent e) {
    myEventDispatcher.getMulticaster().stateChanged(e);
  }
}
