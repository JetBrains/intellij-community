// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class PageContainer<T> {
  private final List<T> myItems = new ArrayList<>();
  private final int myPageSize;
  private int myCurrentPage;
  private boolean myLastPage;

  public PageContainer(int pageSize, int firstPage) {
    myPageSize = pageSize;
    myCurrentPage = firstPage;
  }

  public @NotNull List<T> getItems() {
    return myItems;
  }

  public void addItems(@NotNull List<? extends T> items) {
    myItems.addAll(items);
    myCurrentPage++;
    myLastPage = items.size() < myPageSize;
  }

  public boolean isNextPage() {
    return !myLastPage;
  }

  public int getNextPage() {
    return myCurrentPage + 1;
  }
}