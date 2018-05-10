// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import java.util.List;

public class ContributorSearchResult {
  private final List<Object> items;
  private final boolean hasMoreItems;

  public ContributorSearchResult(List<Object> items, boolean hasMoreItems) {
    this.items = items;
    this.hasMoreItems = hasMoreItems;
  }

  public List<Object> getItems() {
    return items;
  }

  public boolean hasMoreItems() {
    return hasMoreItems;
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }
}
