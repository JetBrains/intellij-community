// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Pass;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.TabInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ActionPanel extends NonOpaquePanel {

  private final List<ActionButton> myButtons = new ArrayList<>();
  private final JBTabsImpl myTabs;

  private boolean myAutoHide;
  private boolean myActionsIsVisible = false;

  public ActionPanel(JBTabsImpl tabs, TabInfo tabInfo, Pass<MouseEvent> pass) {
    myTabs = tabs;
    ActionGroup group = tabInfo.getTabLabelActions() != null ? tabInfo.getTabLabelActions() : new DefaultActionGroup();
    AnAction[] children = group.getChildren(null);

    final NonOpaquePanel wrapper = new NonOpaquePanel(new BorderLayout());
    wrapper.add(Box.createHorizontalStrut(2), BorderLayout.WEST);
    NonOpaquePanel inner = new NonOpaquePanel();
    inner.setLayout(new BoxLayout(inner, BoxLayout.X_AXIS));
    wrapper.add(inner, BorderLayout.CENTER);
    for (AnAction each : children) {
      ActionButton eachButton = new ActionButton(myTabs, tabInfo, each, tabInfo.getTabActionPlace(), pass, tabs.getTabActionsMouseDeadzone()) {
        @Override
        protected void repaintComponent(final Component c) {
          TabLabel tabLabel = (TabLabel) SwingUtilities.getAncestorOfClass(TabLabel.class, c);
          if (tabLabel != null) {
            Point point = SwingUtilities.convertPoint(c, new Point(0, 0), tabLabel);
            Dimension d = c.getSize();
            tabLabel.repaint(point.x, point.y, d.width, d.height);
          } else {
            super.repaintComponent(c);
          }
        }
      };
      
      myButtons.add(eachButton);
      InplaceButton component = eachButton.getComponent();
      inner.add(component);
    }

    add(wrapper);
  }

  public boolean update() {
    boolean changed = false;
    boolean anyVisible = false;
    for (ActionButton each : myButtons) {
      changed |= each.update();
      each.setMouseDeadZone(myTabs.getTabActionsMouseDeadzone());
      anyVisible |= each.getComponent().isVisible();
    }

    myActionsIsVisible = anyVisible;
    
    return changed;
  }

  public boolean isAutoHide() {
    return myAutoHide;
  }

  public void setAutoHide(final boolean autoHide) {
    myAutoHide = autoHide;
    for (ActionButton each : myButtons) {
      each.setAutoHide(myAutoHide);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return myActionsIsVisible ? super.getPreferredSize() : new Dimension(0, 0);
  }

  public void toggleShowActions(final boolean show) {
    for (ActionButton each : myButtons) {
      each.toggleShowActions(show);
    }
  }

}
