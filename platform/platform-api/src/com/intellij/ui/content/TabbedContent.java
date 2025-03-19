// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.content;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

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
  @Unmodifiable
  List<Pair<@Nls String, JComponent>> getTabs();

  default boolean hasMultipleTabs() {
    return getTabs().size() > 1;
  }

  default @NotNull TabGroupId getId() {
    return new TabGroupId(getTitlePrefix(), getTitlePrefix());
  }

  @Nls
  String getTitlePrefix();

  void split();
}
