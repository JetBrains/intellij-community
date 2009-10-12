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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pass;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.TabInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class ActionPanel extends NonOpaquePanel {

  private final ActionGroup myGroup;
  private final List<ActionButton> myButtons = new ArrayList<ActionButton>();
  private final TabInfo myTabInfo;
  private final JBTabsImpl myTabs;

  private boolean myAutoHide;

  private final int myGap = 2;

  public ActionPanel(JBTabsImpl tabs, TabInfo tabInfo, Pass<MouseEvent> pass) {
    myTabs = tabs;
    myTabInfo = tabInfo;
    myGroup = tabInfo.getTabLabelActions() != null ? tabInfo.getTabLabelActions() : new DefaultActionGroup();
    AnAction[] children = myGroup.getChildren(null);

    final NonOpaquePanel wrapper = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    wrapper.add(Box.createHorizontalStrut(myGap));
    for (AnAction each : children) {
      ActionButton eachButton = new ActionButton(myTabs, tabInfo, each, tabInfo.getTabActionPlace(), pass, tabs.getTabActionsMouseDeadzone());
      myButtons.add(eachButton);
      wrapper.add(eachButton.getComponent());
    }

    setLayout(new GridBagLayout());
    add(wrapper);
  }

  public boolean update() {
    boolean changed = false;
    for (ActionButton each : myButtons) {
      changed |= each.update();
      each.setMouseDeadZone(myTabs.getTabActionsMouseDeadzone());
    }

    return changed;
  }

  public boolean isAutoHide() {
    return myAutoHide;
  }

  public void setAutoHide(final boolean autoHide) {
    myAutoHide = autoHide;
    for (Iterator<ActionButton> iterator = myButtons.iterator(); iterator.hasNext();) {
      ActionButton each = iterator.next();
      each.setAutoHide(myAutoHide);
    }
  }

  public void toggleShowActtions(final boolean show) {
    for (Iterator<ActionButton> iterator = myButtons.iterator(); iterator.hasNext();) {
      ActionButton each = iterator.next();
      each.toggleShowActions(show);
    }
  }

}
