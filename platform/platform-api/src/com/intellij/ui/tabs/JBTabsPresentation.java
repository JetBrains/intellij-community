/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.tabs;

import com.intellij.util.ui.TimedDeadzone;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public interface JBTabsPresentation {

  boolean isHideTabs();

  void setHideTabs(boolean hideTabs);

  JBTabsPresentation setPaintBorder(int top, int left, int right, int bottom);
  JBTabsPresentation setTabSidePaintBorder(int size);

  JBTabsPresentation setPaintFocus(boolean paintFocus);

  JBTabsPresentation setAlwaysPaintSelectedTab(final boolean paintSelected);

  JBTabsPresentation setStealthTabMode(boolean stealthTabMode);

  JBTabsPresentation setSideComponentVertical(boolean vertical);

  JBTabsPresentation setSideComponentOnTabs(boolean onTabs);

  JBTabsPresentation setSideComponentBefore(boolean before);

  JBTabsPresentation setSingleRow(boolean singleRow);

  boolean isSingleRow();

  JBTabsPresentation setUiDecorator(@Nullable UiDecorator decorator);

  JBTabsPresentation setRequestFocusOnLastFocusedComponent(boolean request);

  void setPaintBlocked(boolean blocked, final boolean takeSnapshot);

  JBTabsPresentation setInnerInsets(Insets innerInsets);

  JBTabsPresentation setGhostsAlwaysVisible(boolean visible);

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
}
