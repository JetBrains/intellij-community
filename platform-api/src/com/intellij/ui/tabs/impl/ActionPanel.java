package com.intellij.ui.tabs.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pass;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.TabInfo;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

class ActionPanel extends NonOpaquePanel {

  private ActionGroup myGroup;
  private List<ActionButton> myButtons = new ArrayList<ActionButton>();
  private TabInfo myTabInfo;
  private JBTabsImpl myTabs;

  private boolean myAutoHide;

  private int myGap = 2;

  public ActionPanel(JBTabsImpl tabs, TabInfo tabInfo, Pass<MouseEvent> pass) {
    myTabs = tabs;
    myTabInfo = tabInfo;
    myGroup = tabInfo.getTabLabelActions() != null ? tabInfo.getTabLabelActions() : new DefaultActionGroup();
    AnAction[] children = myGroup.getChildren(null);

    setLayout(new GridLayout(1, children.length, 0, myGap));

    for (AnAction each : children) {
      ActionButton eachButton = new ActionButton(myTabs, tabInfo, each, tabInfo.getTabActionPlace(), pass);
      myButtons.add(eachButton);
      add(eachButton.getComponent());
    }
  }

  public boolean update() {
    boolean changed = false;
    for (ActionButton each : myButtons) {
      changed |= each.update();
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
