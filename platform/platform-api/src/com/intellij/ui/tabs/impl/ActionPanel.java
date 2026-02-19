// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBTreeTraverser;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.BoxLayout;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class ActionPanel extends NonOpaquePanel {
  private final List<ActionButton> buttons = new ArrayList<>();
  private final JBTabsImpl tabs;
  private final TabInfo info;

  private boolean myAutoHide;
  private boolean myActionsIsVisible = false;
  private boolean myMarkModified = false;

  public ActionPanel(JBTabsImpl tabs, TabInfo tabInfo, Consumer<? super MouseEvent> pass, Consumer<? super Boolean> hover) {
    this.tabs = tabs;
    info = tabInfo;
    ActionGroup group = tabInfo.getTabLabelActions() != null ? tabInfo.getTabLabelActions() : new DefaultActionGroup();
    ActionManager actionManager = ActionManager.getInstance();
    // TODO replace with a regular toolbar
    List<AnAction> children = JBTreeTraverser.<AnAction>of(
        o -> o instanceof DefaultActionGroup g ? g.getChildren(actionManager) : AnAction.EMPTY_ARRAY)
      .withRoot(group)
      .filter(o -> o.getActionUpdateThread() == ActionUpdateThread.EDT)
      .toList();
    if (LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred() && !UISettings.getInstance().getCloseTabButtonOnTheRight()) {
      children = ContainerUtil.reverse(children);
    }

    setFocusable(false);

    final NonOpaquePanel wrapper = new NonOpaquePanel(new BorderLayout());
    wrapper.setFocusable(false);
    NonOpaquePanel inner = new NonOpaquePanel();
    inner.setLayout(new BoxLayout(inner, BoxLayout.X_AXIS));
    wrapper.add(inner, BorderLayout.CENTER);
    for (AnAction each : children) {
      ActionButton eachButton = new ActionButton(tabInfo, each, tabInfo.getTabActionPlace(), pass, hover, tabs.getTabActionsMouseDeadZone$intellij_platform_ide()) {
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

      buttons.add(eachButton);
      InplaceButton component = eachButton.getComponent();
      component.setFocusable(false);
      inner.add(component);
    }

    add(wrapper);

    UIUtil.uiTraverser(wrapper).forEach(c -> c.setFocusable(false));
  }

  @Override
  public void paint(Graphics g) {
    TabLabel label = tabs.getTabLabel(info);
    boolean isHovered = label != null && label.isHovered();
    boolean isSelected = tabs.getSelectedInfo() == info;
    if (ExperimentalUI.isNewUI()
        && tabs instanceof JBEditorTabs
        && !isSelected
        && !isHovered
        && !myMarkModified
        && !info.isPinned()) {
      return;
    }
    super.paint(g);
  }

  public boolean update() {
    if (getRootPane() == null) return false;
    boolean changed = false;
    boolean anyVisible = false;
    boolean anyModified = false;
    for (ActionButton each : buttons) {
      changed |= each.update();
      each.setMouseDeadZone(tabs.getTabActionsMouseDeadZone$intellij_platform_ide());
      anyVisible |= each.getComponent().isVisible();

      Boolean markModified = each.getPrevPresentation().getClientProperty(JBEditorTabs.MARK_MODIFIED_KEY);
      if (markModified != null) {
        anyModified |= markModified;
      }
    }

    myActionsIsVisible = anyVisible;
    myMarkModified = anyModified;

    return changed;
  }

  public boolean isAutoHide() {
    return myAutoHide;
  }

  public void setAutoHide(final boolean autoHide) {
    myAutoHide = autoHide;
    for (ActionButton each : buttons) {
      each.setAutoHide(myAutoHide);
    }
  }

  @Override
  public Dimension getPreferredSize() {
    return myActionsIsVisible ? super.getPreferredSize() : new Dimension(0, 0);
  }

  public void toggleShowActions(final boolean show) {
    for (ActionButton each : buttons) {
      each.toggleShowActions(show);
    }
  }
}