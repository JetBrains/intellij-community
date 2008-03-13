package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Comparator;
import java.util.List;

public interface JBTabs {

  @NotNull
  TabInfo addTab(TabInfo info, int index);

  @NotNull
  TabInfo addTab(TabInfo info);

  void removeTab(@Nullable TabInfo info);

  void removeTab(@Nullable TabInfo info, boolean transferFocus);

  void removeAllTabs();

  @Nullable
  ActionGroup getPopupGroup();

  @Nullable
  String getPopupPlace();

  void setPopupGroup(@NotNull ActionGroup popupGroup, @NotNull String place);

  ActionCallback select(@NotNull TabInfo info, boolean requestFocus);

  @Nullable
  TabInfo getSelectedInfo();

  @Nullable
  TabInfo getTabAt(int tabIndex);

  @NotNull
  List<TabInfo> getTabs();

  int getTabCount();

  boolean isHideTabs();

  void setHideTabs(boolean hideTabs);

  boolean isPaintBorder();

  void setPaintBorder(boolean paintBorder);

  boolean isPaintFocus();

  void setPaintFocus(boolean paintFocus);

  void setStealthTabMode(boolean stealthTabMode);

  boolean isStealthTabMode();

  void setSideComponentVertical(boolean vertical);

  void setSingleRow(boolean singleRow);

  boolean isSingleRow();

  boolean isSideComponentVertical();

  void setUiDecorator(@Nullable UiDecorator decorator);

  @Nullable
  DataProvider getDataProvider();

  JBTabs setDataProvider(@NotNull DataProvider dataProvider);

  @Nullable
  TabInfo getTargetInfo();

  @NotNull
  JBTabs addTabMouseListener(@NotNull MouseListener listener);

  @NotNull
  JBTabs removeTabMouseListener(@NotNull MouseListener listener);

  JBTabs addListener(@NotNull TabsListener listener);

  @NotNull
  JComponent getComponent();

  void updateTabActions(boolean validateNow);

  @Nullable
  TabInfo findInfo(Component component);

  @Nullable
  TabInfo findInfo(MouseEvent event);

  boolean isRequestFocusOnLastFocusedComponent();

  void setRequestFocusOnLastFocusedComponent(boolean requestFocusOnLastFocusedComponent);

  void sortTabs(Comparator<TabInfo> comparator);

  void setPaintBlocked(boolean blocked);

  void setFocused(boolean focused);

  interface UiDecorator {
    @NotNull
    JBTabsImpl.UiDecoration getDecoration();
  }
}
