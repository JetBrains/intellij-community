// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is marker interface for an Essential Contributor. During the search process, it is guaranteed
 * that results are not displayed until all contributions marked as Essential have returned items
 * (or have completed processing with no results).
 */
public interface EssentialContributor {

  /**
   * Defines whether the contributor should be considered essential by default.
   * This behavior may be altered by a machine learning model (the non-default behavior).
   *
   * @see EssentialContributor#checkEssential(SearchEverywhereContributor)
   */
  default boolean isEssentialByDefault() {
    return true;
  }

  static boolean checkEssentialByDefault(SearchEverywhereContributor<?> contributor) {
    return (contributor instanceof EssentialContributor ic) && ic.isEssentialByDefault();
  }

  static boolean checkEssential(SearchEverywhereContributor<?> contributor) {
    Boolean isEssentialByMl = checkEssentialByMl(contributor);
    if (isEssentialByMl != null) {
      return isEssentialByMl;
    }
    else {
      return checkEssentialByDefault(contributor);
    }
  }
  /**
   * Uses machine learning (through ML in Search Everywhere plugin) to determine if a contribution is essential or not.
   * May return null if the plugin is unavailable.
   */
  private static @Nullable Boolean checkEssentialByMl(@NotNull SearchEverywhereContributor<?> contributor) {
    var marker = SearchEverywhereEssentialContributorMarker.getInstanceOrNull();
    if (marker == null) return null;

    return marker.isContributorEssential(contributor);
  }
}
