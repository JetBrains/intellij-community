// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.TimedDeadzone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface JBTabsPresentation {

  boolean isHideTabs();

  void setHideTabs(boolean hideTabs);

  /**
   * @deprecated You should implement {@link JBTabsBorder} abstract class
   */
  @Deprecated(forRemoval = true)
  JBTabsPresentation setPaintBorder(int top, int left, int right, int bottom);
  /**
   * @deprecated You should implement {@link JBTabsBorder} abstract class
   */
  @Deprecated(forRemoval = true)
  JBTabsPresentation setTabSidePaintBorder(int size);

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
