// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

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
}
