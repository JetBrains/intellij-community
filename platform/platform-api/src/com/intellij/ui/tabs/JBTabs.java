// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.ui.DropAreaAware;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public interface JBTabs extends DropAreaAware {
  @NotNull
  TabInfo addTab(TabInfo info, int index);

  @NotNull
  TabInfo addTab(TabInfo info);

  @NotNull
  ActionCallback removeTab(@Nullable TabInfo info);
  void removeAllTabs();

  @NotNull
  ActionCallback select(@NotNull TabInfo info, boolean requestFocus);

  @Nullable
  TabInfo getSelectedInfo();

  @NotNull
  TabInfo getTabAt(int tabIndex);

  int getTabCount();

  @NotNull
  JBTabsPresentation getPresentation();

  @NotNull
  List<TabInfo> getTabs();

  @Nullable
  TabInfo getTargetInfo();

  @NotNull
  JBTabs addTabMouseListener(@NotNull MouseListener listener);

  @NotNull
  JBTabs removeTabMouseListener(@NotNull MouseListener listener);

  JBTabs addListener(@NotNull TabsListener listener);

  JBTabs addListener(@NotNull TabsListener listener, @Nullable Disposable disposable);

  JBTabs setSelectionChangeHandler(SelectionChangeHandler handler);

  @NotNull
  JComponent getComponent();

  @Nullable
  TabInfo findInfo(MouseEvent event);

  @Nullable
  TabInfo findInfo(Object object);

  @Nullable
  TabInfo findInfo(Component component);

  int getIndexOf(@NotNull TabInfo tabInfo);

  void requestFocus();

  void setNavigationActionBinding(String prevActionId, String nextActionId);

  @NotNull
  JBTabs setPopupGroup(@NotNull ActionGroup popupGroup, @NotNull String place, boolean addNavigationGroup);

  @NotNull
  JBTabs setPopupGroup(@NotNull Supplier<? extends ActionGroup> popupGroup, @NotNull String place, boolean addNavigationGroup);

  void resetDropOver(TabInfo tabInfo);
  Image startDropOver(TabInfo tabInfo, RelativePoint point);
  void processDropOver(TabInfo over, RelativePoint point);

  @Nullable Component getTabLabel(TabInfo tabInfo);

  @Override
  default @NotNull Rectangle getDropArea() {
    Rectangle r = new Rectangle(new Point(0, 0), getComponent().getSize());
    if (getTabCount() > 0) {
      @SuppressWarnings("UseDPIAwareInsets")
      Insets insets = JBUI.insets(0);
      Rectangle bounds = Objects.requireNonNull(getTabLabel(getTabAt(0))).getBounds();
      switch (getPresentation().getTabsPosition()) {
        case top -> insets.top = bounds.height;
        case left -> insets.left = bounds.width;
        case bottom -> insets.bottom = bounds.height;
        case right -> insets.right = bounds.width;
      }
      JBInsets.removeFrom(r, insets);
    }
    return r;
  }


  interface SelectionChangeHandler {
    @NotNull ActionCallback execute(TabInfo info, boolean requestFocus, @NotNull ActiveRunnable doChangeSelection);
  }
}
