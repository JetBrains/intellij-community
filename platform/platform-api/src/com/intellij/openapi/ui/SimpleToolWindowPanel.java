// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider;
import com.intellij.openapi.util.Key;
import com.intellij.ui.*;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.util.ui.UIUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.List;

public class SimpleToolWindowPanel extends JBPanelWithEmptyText implements QuickActionProvider, UiCompatibleDataProvider {
  public static final Key<Boolean> SCROLLED_STATE = Key.create("ScrolledState");
  private static final int GAP = 1;

  private JComponent myToolbar;
  private JComponent myContent;

  private final boolean myBorderless;
  protected boolean myVertical;
  private boolean myProvideQuickActions = true;

  private @Nullable ScrollPaneTracker myScrollPaneTracker;

  public SimpleToolWindowPanel(boolean vertical) {
    this(vertical, false);
  }

  public SimpleToolWindowPanel(boolean vertical, boolean borderless) {
    myBorderless = borderless;
    myVertical = vertical;
    updateLayout();

    addContainerListener(new ContainerAdapter() {
      @Override
      public void componentAdded(ContainerEvent e) {
        Component child = e.getChild();

        if (child instanceof Container) {
          ((Container)child).addContainerListener(this);
        }
        if (myBorderless) {
          UIUtil.removeScrollBorder(SimpleToolWindowPanel.this);
        }
      }

      @Override
      public void componentRemoved(ContainerEvent e) {
        Component child = e.getChild();

        if (child instanceof Container) {
          ((Container)child).removeContainerListener(this);
        }
      }
    });

    if (ExperimentalUI.isNewUI()) {
      myScrollPaneTracker = new ScrollPaneTracker(this, this::isInContent, tracker -> {
        updateScrolledState();
        return Unit.INSTANCE;
      });
    }
  }

  private boolean isInContent(@NotNull Component component) {
    // This check is just in case we have something scrollable in the toolbar.
    var parent = component;
    while (parent != null) {
      if (parent == myToolbar) {
        return false;
      }
      else if (parent == myContent) {
        return true;
      }
      parent = parent.getParent();
    }
    return false;
  }

  public boolean isVertical() {
    return myVertical;
  }

  public void setVertical(boolean vertical) {
    if (myVertical == vertical) return;
    removeAll();
    myVertical = vertical;
    updateLayout();
    setContent(myContent);
    setToolbar(myToolbar);
  }

  public boolean isToolbarVisible() {
    return myToolbar != null && myToolbar.isVisible();
  }

  public @Nullable JComponent getToolbar() {
    return myToolbar;
  }

  private void updateLayout() {
    setLayout(new BorderLayout(myVertical ? 0 : GAP, myVertical ? GAP : 0));
  }

  public void setToolbar(@Nullable JComponent c) {
    if (c == null) {
      JComponent toolbar = myToolbar;
      if (toolbar != null) {
        remove(toolbar);
      }
    }
    myToolbar = c;
    if (myToolbar instanceof ActionToolbar) {
      ((ActionToolbar)myToolbar).setOrientation(myVertical ? SwingConstants.HORIZONTAL : SwingConstants.VERTICAL);
    }

    if (c != null) {
      if (myVertical) {
        add(c, BorderLayout.NORTH);
      }
      else {
        add(c, BorderLayout.WEST);
      }
    }

    revalidate();
    repaint();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    if (myProvideQuickActions) {
      sink.set(QuickActionProvider.KEY, this);
    }
  }

  public SimpleToolWindowPanel setProvideQuickActions(boolean provide) {
    myProvideQuickActions = provide;
    return this;
  }

  @Override
  public @Unmodifiable @NotNull List<AnAction> getActions(boolean originalProvider) {
    return collectActions(myToolbar);
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  public @Nullable JComponent getContent() {
    return myContent;
  }

  public void setContent(@NotNull JComponent c) {
    if (myContent != null) {
      ClientProperty.remove(myContent, SCROLLED_STATE);
      remove(myContent);
    }

    myContent = c;

    add(c, BorderLayout.CENTER);

    if (myBorderless) {
      UIUtil.removeScrollBorder(c);
    }

    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (myToolbar != null && myToolbar.getParent() == this && myContent != null && myContent.getParent() == this) {
      g.setColor(JBColor.border());

      if (ExperimentalUI.isNewUI() && !isScrolled()) {
          return;
      }
      if (myVertical) {
        int y = (int)myToolbar.getBounds().getMaxY();
        LinePainter2D.paint((Graphics2D)g, 0, y, getWidth(), y);
      }
      else {
        int x = (int)myToolbar.getBounds().getMaxX();
        LinePainter2D.paint((Graphics2D)g, x, 0, x, getHeight());
      }
    }
  }

  public static @Unmodifiable @NotNull List<AnAction> collectActions(@Nullable JComponent component) {
    return UIUtil.uiTraverser(component).traverse()
      .filter(ActionToolbar.class)
      .map(ActionToolbar::getActionGroup)
      .filter(AnAction.class)
      .toList();
  }

  private void updateScrolledState() {
    if (myContent == null || myScrollPaneTracker == null) {
      return;
    }
    var oldState = isScrolled();
    var newState = false;
    for (ScrollPaneScrolledState scrollPaneState : myScrollPaneTracker.getScrollPaneStates()) {
      var scrollPane = scrollPaneState.getScrollPane();
      boolean scrolled = myVertical ? !scrollPaneState.getState().isVerticalAtStart() : !scrollPaneState.getState().isHorizontalAtStart();
      if (isTouchingToolbar(scrollPane) && scrolled) {
        newState = true;
        break;
      }
    }
    if (newState != oldState) {
      ClientProperty.put(myContent, SCROLLED_STATE, newState);
      repaint();
    }
    var key = myVertical ? ScrollableContentBorder.TOOLBAR_WITH_BORDER_ABOVE : ScrollableContentBorder.TOOLBAR_WITH_BORDER_LEFT;
    for (ScrollPaneScrolledState scrollPaneState : myScrollPaneTracker.getScrollPaneStates()) {
      var targetComponent = ScrollableContentBorder.getTargetComponent(scrollPaneState.getScrollPane());
      if (targetComponent == null) {
        continue;
      }
      var hadToolbarWithBorder = ClientProperty.isTrue(targetComponent, key);
      var hasToolbarWithBorder = isTouchingToolbar(targetComponent) && (!ExperimentalUI.isNewUI() || newState);
      if (hasToolbarWithBorder != hadToolbarWithBorder) {
        ClientProperty.put(targetComponent, key, hasToolbarWithBorder);
        targetComponent.repaint();
      }
    }
  }

  private boolean isScrolled() {
    return ClientProperty.isTrue(myContent, SCROLLED_STATE);
  }

  private boolean isTouchingToolbar(JComponent component) {
    var toolbar = getToolbar();
    if (toolbar == null || !toolbar.isVisible() || !component.isShowing()) {
      return false;
    }
    var toolbarBounds = SwingUtilities.convertRectangle(toolbar.getParent(), toolbar.getBounds(), this);
    var expectedCoordinate = (myVertical ? toolbarBounds.y + toolbarBounds.height : toolbarBounds.x + toolbarBounds.width) + GAP;
    var paneLocation = SwingUtilities.convertPoint(component.getParent(), component.getLocation(), this);
    var actualCoordinate = myVertical ? paneLocation.y : paneLocation.x;
    return expectedCoordinate == actualCoordinate;
  }

}
