// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Class containing info about found elements
 */
public class SearchEverywhereFoundElementInfo {
  @ApiStatus.Internal
  public final @Nullable String uuid;

  public final int priority;
  public final Object element;
  public final SearchEverywhereContributor<?> contributor;
  @ApiStatus.Internal
  public final SearchEverywhereSpellCheckResult correction;

  public SearchEverywhereFoundElementInfo(Object element, int priority, SearchEverywhereContributor<?> contributor) {
    this(null, element, priority, contributor, SearchEverywhereSpellCheckResult.NoCorrection.INSTANCE);
  }

  public SearchEverywhereFoundElementInfo(@Nullable String uuid, Object element, int priority, SearchEverywhereContributor<?> contributor) {
    this(uuid, element, priority, contributor, SearchEverywhereSpellCheckResult.NoCorrection.INSTANCE);
  }

  public SearchEverywhereFoundElementInfo(Object element, int priority,
                                          SearchEverywhereContributor<?> contributor,
                                          SearchEverywhereSpellCheckResult correction) {
    this(null, element, priority, contributor, correction);
  }

  @ApiStatus.Internal
  public SearchEverywhereFoundElementInfo(@Nullable String uuid, Object element, int priority,
                                          SearchEverywhereContributor<?> contributor,
                                          SearchEverywhereSpellCheckResult correction) {
    this.uuid = uuid;
    this.priority = priority;
    this.element = element;
    this.contributor = contributor;
    this.correction = correction;
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

  @ApiStatus.Internal
  public SearchEverywhereSpellCheckResult getCorrection() {
    return correction;
  }

  public String getDescription() {
    return "contributor: " + (contributor != null ? contributor.getSearchProviderId() : "null") + "\n" +
           "weight: " + priority + "\n" +
           "corrected by spell checker: " +
           (correction instanceof SearchEverywhereSpellCheckResult.Correction ?
            "Yes (correction: \"" + ((SearchEverywhereSpellCheckResult.Correction)correction).getCorrection() + "\", confidence: " +
            String.format("%.2f", ((SearchEverywhereSpellCheckResult.Correction)correction).getConfidence()) + ")" : "No");

  }

  public static final Comparator<SearchEverywhereFoundElementInfo> COMPARATOR = (o1, o2) -> {
    int res = Integer.compare(o1.priority, o2.priority);
    if (res != 0) return res;

    return -Integer.compare(o1.contributor.getSortWeight(), o2.contributor.getSortWeight());
  };
}
