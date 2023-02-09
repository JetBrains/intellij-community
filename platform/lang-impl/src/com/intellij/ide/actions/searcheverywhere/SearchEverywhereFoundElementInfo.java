// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.ApiStatus;

import java.util.Comparator;

/**
 * Class containing info about found elements
 */
@ApiStatus.Internal
public class SearchEverywhereFoundElementInfo {
  public final int priority;
  public final Object element;
  public final SearchEverywhereContributor<?> contributor;

  public SearchEverywhereFoundElementInfo(Object element, int priority, SearchEverywhereContributor<?> contributor) {
    this.priority = priority;
    this.element = element;
    this.contributor = contributor;
  }

  public int getPriority() {
    return priority;
  }

  public Object getElement() {
    return element;
  }

  public SearchEverywhereContributor<?> getContributor() {
    return contributor;
  }

  public String getDescription() {
    return "contributor: " + (contributor != null ? contributor.getSearchProviderId() : "null") + "\n" +
           "weight: " + priority + "\n";
  }

  public static final Comparator<SearchEverywhereFoundElementInfo> COMPARATOR = (o1, o2) -> {
    int res = Integer.compare(o1.priority, o2.priority);
    if (res != 0) return res;

    return -Integer.compare(o1.contributor.getSortWeight(), o2.contributor.getSortWeight());
  };
}
