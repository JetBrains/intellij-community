// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import java.util.*;

public class ContributorSearchResult<T> {
  private final List<T> items;
  private final boolean hasMoreItems;

  public static <E> Builder<E> builder() {
    return new Builder<>();
  }

  private ContributorSearchResult(List<T> items, boolean hasMoreItems) {
    this.items = items;
    this.hasMoreItems = hasMoreItems;
  }

  public List<T> getItems() {
    return items;
  }

  public boolean hasMoreItems() {
    return hasMoreItems;
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  //todo is multithreading possible? #UX-1
  public static class Builder<T> {
    private final Set<T> items = new LinkedHashSet<>();
    private boolean hasMore;

    public void addItem(T item) {
      items.add(item);
    }

    public void setHasMore(boolean hasMore) {
      this.hasMore = hasMore;
    }

    public int itemsCount() {
      return items.size();
    }

    public ContributorSearchResult<T> build() {
      List<T> list = Collections.unmodifiableList(new ArrayList<>(items));
      return new ContributorSearchResult<>(list, hasMore);
    }
  }
}
