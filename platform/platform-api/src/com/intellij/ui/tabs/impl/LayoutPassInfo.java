/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.tabs.impl;

import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public abstract class LayoutPassInfo {

  public final List<TabInfo> myVisibleInfos;

  protected LayoutPassInfo(List<TabInfo> visibleInfos) {
    myVisibleInfos = visibleInfos;
  }

  @Nullable
  public abstract TabInfo getPreviousFor(TabInfo info);

  @Nullable
  public abstract TabInfo getNextFor(TabInfo info);

  @Nullable
  public static TabInfo getPrevious(List<TabInfo> list, int i) {
    return i > 0 ? list.get(i - 1) : null;
  }

  @Nullable
  public static TabInfo getNext(List<TabInfo> list, int i) {
    return i < list.size() - 1 ? list.get(i + 1) : null;
  }

  public abstract int getRowCount();

  public abstract int getColumnCount(int row);

  public abstract TabInfo getTabAt(int row, int column);

  public abstract boolean hasCurveSpaceFor(final TabInfo tabInfo);
  
  public abstract Rectangle getHeaderRectangle();
}
