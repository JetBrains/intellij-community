package com.intellij.ui.tabs.impl;

import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnAction;

import java.util.List;
import java.util.ArrayList;
import java.awt.*;

class ActionPanel extends NonOpaquePanel {

  private ActionGroup myGroup;
  private List<ActionButton> myButtons = new ArrayList<ActionButton>();
  private TabInfo myTabInfo;
  private JBTabsImpl myTabs;

  public ActionPanel(JBTabsImpl tabs, TabInfo tabInfo) {
    myTabs = tabs;
    myTabInfo = tabInfo;
    myGroup = tabInfo.getTabLabelActions() != null ? tabInfo.getTabLabelActions() : new DefaultActionGroup();
    AnAction[] children = myGroup.getChildren(null);
    setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
    for (AnAction each : children) {
      ActionButton eachButton = new ActionButton(myTabs, tabInfo, each, tabInfo.getTabActionPlace());
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

  public Dimension getPreferredSize() {
    for (ActionButton each : myButtons) {
      if (each.getComponent().isVisible()) {
        return super.getPreferredSize();
      }
    }
    return new Dimension(0, 0);
  }
}
