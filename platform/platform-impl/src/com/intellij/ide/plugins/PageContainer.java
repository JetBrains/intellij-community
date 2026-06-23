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
  private int myCurrentPage;
  private boolean myHasNextPage;

  public PageContainer(int firstPage, boolean hasNextPage) {
    myCurrentPage = firstPage;
    myHasNextPage = hasNextPage;
  }

  public PageContainer(int firstPage, boolean hasNextPage, List<T> items) {
    myCurrentPage = firstPage;
    myHasNextPage = hasNextPage;
    myItems.addAll(items);
  }

  public @NotNull List<T> getItems() {
    return myItems;
  }

  public int getCurrentPage() {
    return myCurrentPage;
  }

  public boolean hasNextPage() {
    return myHasNextPage;
  }
}