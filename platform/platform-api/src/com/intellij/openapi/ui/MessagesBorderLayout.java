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
package com.intellij.openapi.ui;

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
