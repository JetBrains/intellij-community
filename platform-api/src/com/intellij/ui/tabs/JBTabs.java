package com.intellij.ui.tabs;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Getter;
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

  ActionCallback removeTab(@Nullable TabInfo info);

  ActionCallback removeTab(@Nullable TabInfo info, boolean transferFocus);

  void removeAllTabs();

  @Nullable
  ActionGroup getPopupGroup();

  @Nullable
  String getPopupPlace();

  JBTabs setPopupGroup(@NotNull ActionGroup popupGroup, @NotNull String place, final boolean addNavigationGroup);
  JBTabs setPopupGroup(@NotNull Getter<ActionGroup> popupGroup, @NotNull String place, final boolean addNavigationGroup);
  
  ActionCallback select(@NotNull TabInfo info, boolean requestFocus);

  @Nullable
  TabInfo getSelectedInfo();

  @NotNull
  TabInfo getTabAt(int tabIndex);

  @NotNull
  List<TabInfo> getTabs();

  int getTabCount();

  boolean isHideTabs();

  void setHideTabs(boolean hideTabs);

  JBTabs setPaintBorder(int top, int left, int right, int bottom);

  boolean isPaintFocus();

  void setPaintFocus(boolean paintFocus);

  void setStealthTabMode(boolean stealthTabMode);

  boolean isStealthTabMode();

  void setSideComponentVertical(boolean vertical);

  void setSingleRow(boolean singleRow);

  boolean isSingleRow();

  boolean isSideComponentVertical();

  JBTabs setUiDecorator(@Nullable UiDecorator decorator);

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

  @Nullable
  TabInfo findInfo(Object object);

  boolean isRequestFocusOnLastFocusedComponent();

  void setRequestFocusOnLastFocusedComponent(boolean requestFocusOnLastFocusedComponent);

  void sortTabs(Comparator<TabInfo> comparator);

  void setPaintBlocked(boolean blocked);

  void setFocused(boolean focused);

  int getIndexOf(@Nullable final TabInfo tabInfo);

  JBTabs setInnerInsets(Insets innerInsets);

  Insets getInnerInsets();

  JBTabs setGhostsAlwaysVisible(boolean visible);

  boolean isGhostsAlwaysVisible();

  interface UiDecorator {
    @NotNull
    UiDecoration getDecoration();
  }

  @NotNull
  JBTabs setAdjustBorders(boolean adjust);


  class UiDecoration {
    private @Nullable Font myLabelFont;
    private @Nullable Insets myLabelInsets;

    public UiDecoration(final Font labelFont, final Insets labelInsets) {
      myLabelFont = labelFont;
      myLabelInsets = labelInsets;
    }

    @Nullable
    public Font getLabelFont() {
      return myLabelFont;
    }

    @Nullable
    public Insets getLabelInsets() {
      return myLabelInsets;
    }
  }
}
