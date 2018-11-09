// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.messages;

import java.awt.*;

class MessagesBorderLayout extends BorderLayout {
  private double myPhase = 0;//it varies from 0 (hidden state) to 1 (fully visible)

  public double getPhase() {
    return myPhase;
  }

  public void setPhase(double phase) {
    myPhase = phase;
  }

  MessagesBorderLayout() {
  }

  @Override
  public void layoutContainer(Container target) {
    final Dimension realSize = target.getSize();
    target.setSize(target.getPreferredSize());

    super.layoutContainer(target);

    target.setSize(realSize);

    synchronized (target.getTreeLock()) {
      int yShift = (int)((1 - myPhase) * target.getPreferredSize().height);
      Component[] components = target.getComponents();
      for (Component component : components) {
        Point point = component.getLocation();
        point.y -= yShift;
        component.setLocation(point);
      }
    }
  }
}
