// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.util.containers.WeakList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.util.List;

public class WeakFocusStackManager {

  private final WeakList <Component> focusOwners = new WeakList<>();

  private static final WeakFocusStackManager instance = new WeakFocusStackManager();

  public Component getLastFocusedOutside(Container container) {
    Component[] components = focusOwners.toStrongList().toArray(new Component[0]);
    for (int i = components.length - 1; i >= 0; i--) {
      if (!SwingUtilities.isDescendingFrom(components[i], container)) {
        return components[i];
      }
    }
    return null;
  }

  public static WeakFocusStackManager getInstance() {
    return instance;
  }

  private WeakFocusStackManager () {
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

  public Component getLastFocusedComponent() {
    List<Component> strongList = focusOwners.toStrongList();
    return strongList.isEmpty() ? null : strongList.get(strongList.size() - 1);
  }

  public Component getLastButOneFocusedComponent() {
    List<Component> strongList = focusOwners.toStrongList();
    return strongList.size() < 2 ? null : strongList.get(strongList.size() - 2);
  }
}