// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.util.List;

public final class WeakFocusStackManager {
  private final WeakList<Component> focusOwners = new WeakList<>();

  private static final WeakFocusStackManager instance = new WeakFocusStackManager();

  @NotNull
  public static WeakFocusStackManager getInstance() {
    return instance;
  }

  private WeakFocusStackManager() {
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        // We are interested only in FOCUS_GAINED events
        if (event.getID() == FocusEvent.FOCUS_GAINED) {
          focusOwners.add((Component)event.getSource());
        }
      }
    }, AWTEvent.FOCUS_EVENT_MASK);
  }

  @Nullable
  public Component getLastFocusedOutside(Container container) {
    List<Component> components = focusOwners.toStrongList();
    for (int i = components.size() - 1; i >= 0; i--) {
      if (!SwingUtilities.isDescendingFrom(components.get(i), container)) {
        return components.get(i);
      }
    }
    return null;
  }
}