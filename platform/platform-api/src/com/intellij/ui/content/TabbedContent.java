/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui.content;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public interface TabbedContent extends Content {
  String SPLIT_PROPERTY_PREFIX = "tabbed.toolwindow.expanded.";

  void addContent(@NotNull JComponent content, @NlsContexts.TabTitle @NotNull String name, boolean selectTab);

  default void addContent(@NotNull TabDescriptor tab, boolean selectTab) {
    addContent(tab.getComponent(), tab.getDisplayName(), selectTab);
  }

  void removeContent(@NotNull JComponent content);

  /**
   * This method is used for preselecting popup menu items
   *
   * @return index of selected tab
   * @see #selectContent(int)
   */
  default int getSelectedIndex() { return -1; }

  /**
   * This method is invoked before content is selected with {@link ContentManager#setSelectedContent(Content)}
   *
   * @param index index of tab in {@link #getTabs()}
   */
  void selectContent(int index);

  @NotNull
  List<Pair<@Nls String, JComponent>> getTabs();

  default boolean hasMultipleTabs() {
    return getTabs().size() > 1;
  }

  @NotNull
  default TabGroupId getId() {
    return new TabGroupId(getTitlePrefix(), getTitlePrefix());
  }

  @Nls
  String getTitlePrefix();

  void split();
}
