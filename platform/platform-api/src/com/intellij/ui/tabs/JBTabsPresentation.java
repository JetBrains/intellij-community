// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.TimedDeadzone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface JBTabsPresentation {
  boolean isHideTabs();

  void setHideTabs(boolean hideTabs);

  JBTabsPresentation setPaintFocus(boolean paintFocus);

  JBTabsPresentation setSideComponentVertical(boolean vertical);

  JBTabsPresentation setSideComponentOnTabs(boolean onTabs);

  JBTabsPresentation setSideComponentBefore(boolean before);

  JBTabsPresentation setSingleRow(boolean singleRow);

  boolean isSingleRow();

  JBTabsPresentation setUiDecorator(@Nullable UiDecorator decorator);

  JBTabsPresentation setRequestFocusOnLastFocusedComponent(boolean request);

  void setPaintBlocked(boolean blocked, final boolean takeSnapshot);

  JBTabsPresentation setInnerInsets(Insets innerInsets);

  JBTabsPresentation setFocusCycle(final boolean root);

  @NotNull
  JBTabsPresentation setToDrawBorderIfTabsHidden(boolean draw);

  @NotNull
  JBTabs getJBTabs();

  @NotNull
  JBTabsPresentation setActiveTabFillIn(@Nullable Color color);

  @NotNull
  JBTabsPresentation setTabLabelActionsAutoHide(boolean autoHide);

  @NotNull
  JBTabsPresentation setTabLabelActionsMouseDeadzone(TimedDeadzone.Length length);

  @NotNull
  JBTabsPresentation setTabsPosition(JBTabsPosition position);

  JBTabsPosition getTabsPosition();

  JBTabsPresentation setTabDraggingEnabled(boolean enabled);

  JBTabsPresentation setAlphabeticalMode(boolean alphabeticalMode);

  JBTabsPresentation setSupportsCompression(boolean supportsCompression);

  void setFirstTabOffset(int offset);

  JBTabsPresentation setEmptyText(@Nullable @NlsContexts.StatusText String text);
}
