// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.RunAnythingSearchListModel;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

public abstract class RunAnythingGroupBase extends RunAnythingGroup {
  /**
   * Adds limited number of matched items into the list.
   *
   * @param model           needed to avoid adding duplicates into the list
   * @param pattern         input search string
   * @param textToMatch     an item presentation text to be matched with
   * @param isInsertionMode if true gets {@link #getMaxItemsToInsert()} group items, else limits to {@link #getMaxInitialItems()}
   * @param item            a new item that is conditionally added into the model
   * @return true if limit exceeded
   */
  boolean addToList(@NotNull RunAnythingSearchListModel model,
                    @NotNull SearchResult result,
                    @NotNull String pattern,
                    @NotNull String textToMatch,
                    boolean isInsertionMode,
                    @NotNull RunAnythingItem item) {
    if (!model.contains(item) && NameUtil.buildMatcher("*" + pattern).build().matches(textToMatch)) {
      if (result.size() == (isInsertionMode ? getMaxItemsToInsert() : getMaxInitialItems())) {
        result.setNeedMore(true);
        return true;
      }
      result.add(item);
    }
    return false;
  }
}