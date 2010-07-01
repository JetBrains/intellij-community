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
package com.intellij.ui.tabs.impl;

import com.intellij.ui.tabs.JBTabsPresentation;
import com.intellij.ui.tabs.JBTabsPosition;

import java.awt.*;

public class TabsBorder {

  private Insets myBorderSize;
  private int myTabBorderSize;

  private final JBTabsImpl myTabs;

  private JBTabsPosition myPosition;

  private Insets myEffectiveBorder;

  public TabsBorder(JBTabsImpl tabs) {
    myTabs = tabs;
    myBorderSize = new Insets(tabs.getBorder(-1), tabs.getBorder(-1), tabs.getBorder(-1), tabs.getBorder(-1));
    myTabBorderSize = tabs.getBorder(-1);
  }

  public JBTabsPresentation setPaintBorder(int top, int left, int right, int bottom) {
    final Insets newBorder = new Insets(myTabs.getBorder(top), myTabs.getBorder(left), myTabs.getBorder(bottom), myTabs.getBorder(right));
    if (newBorder.equals(myBorderSize)) return myTabs;

    myBorderSize = newBorder;

    myEffectiveBorder = null;

    myTabs.relayout(true, false);

    return myTabs;
  }

  public JBTabsPresentation setTabSidePaintBorder(int size) {
    final int newSize = myTabs.getBorder(size);
    if (myTabBorderSize == newSize) return myTabs;

    myTabBorderSize = newSize;
    myEffectiveBorder = null;

    myTabs.revalidateAndRepaint(false);

    return myTabs;
  }

  public int getTabBorderSize() {
    return myTabBorderSize;
  }

  public Insets getEffectiveBorder() {
    if (myEffectiveBorder != null && myTabs.getTabsPosition() == myPosition) return (Insets)myEffectiveBorder.clone();

    myPosition = myTabs.getTabsPosition();

    myEffectiveBorder = new Insets(
      myPosition == JBTabsPosition.top ? myTabBorderSize : myBorderSize.top,
      myPosition == JBTabsPosition.left ? myTabBorderSize : myBorderSize.left,
      myPosition == JBTabsPosition.bottom ? myTabBorderSize : myBorderSize.bottom,
      myPosition == JBTabsPosition.right ? myTabBorderSize : myBorderSize.right
    );
    return (Insets)myEffectiveBorder.clone();
  }
}
