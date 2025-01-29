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

  default boolean isEssential() {
    return true;
  }

  static boolean checkEssential(SearchEverywhereContributor<?> contributor) {
    return (contributor instanceof EssentialContributor ic) && ic.isEssential();
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
