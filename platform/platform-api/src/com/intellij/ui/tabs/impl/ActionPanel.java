// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class ActionPanel extends NonOpaquePanel {
  private final List<ActionButton> myButtons = new ArrayList<>();
  private final JBTabsImpl myTabs;

  private boolean myAutoHide;
  private boolean myActionsIsVisible = false;

  public ActionPanel(JBTabsImpl tabs, TabInfo tabInfo, Consumer<MouseEvent> pass, Consumer<Boolean> hover) {
    myTabs = tabs;
    ActionGroup group = tabInfo.getTabLabelActions() != null ? tabInfo.getTabLabelActions() : new DefaultActionGroup();
    AnAction[] children = group.getChildren(null);
    if(!UISettings.getShadowInstance().getCloseTabButtonOnTheRight()) {
      List<AnAction> list = Arrays.asList(children);
      Collections.reverse(list);
      children = list.toArray(AnAction[]::new);
    }

    setFocusable(false);

    final NonOpaquePanel wrapper = new NonOpaquePanel(new BorderLayout());
    wrapper.setFocusable(false);
    wrapper.add(Box.createHorizontalStrut(2), BorderLayout.WEST);
    NonOpaquePanel inner = new NonOpaquePanel();
    inner.setLayout(new BoxLayout(inner, BoxLayout.X_AXIS));
    wrapper.add(inner, BorderLayout.CENTER);
    for (AnAction each : children) {
      ActionButton eachButton = new ActionButton(tabInfo, each, tabInfo.getTabActionPlace(), pass, hover, tabs.getTabActionsMouseDeadzone()) {
        @Override
        protected void repaintComponent(final Component c) {
          TabLabel tabLabel = (TabLabel) SwingUtilities.getAncestorOfClass(TabLabel.class, c);
          if (tabLabel != null) {
            Point point = SwingUtilities.convertPoint(c, new Point(0, 0), tabLabel);
            Dimension d = c.getSize();
            tabLabel.repaint(point.x, point.y, d.width, d.height);
          }
          else {
            super.repaintComponent(c);
          }
        }
      };

      myButtons.add(eachButton);
      InplaceButton component = eachButton.getComponent();
      component.setFocusable(false);
      inner.add(component);
    }

    add(wrapper);

    UIUtil.uiTraverser(wrapper).forEach(c -> c.setFocusable(false));
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