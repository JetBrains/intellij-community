// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus

/**
 * The section limit of one Search Everywhere contributor, carried on the progress indicator of its search.
 *
 * The value is the total number of items the section takes, items already shown included.
 * The section shows "more" when the contributor produces one item over the limit.
 * Goto contributors pass it on as [com.intellij.util.indexing.FindSymbolParameters.getLimit], see [GotoLimitedRuns].
 */
@ApiStatus.Internal
object SearchEverywhereElementsLimit {
  private val KEY: Key<Int> = Key.create("SearchEverywhereElementsLimit")

  /**
   * Returns the limit set on [indicator], or 0 when there is none.
   */
  @JvmStatic
  fun of(indicator: ProgressIndicator): Int = (indicator as? UserDataHolder)?.getUserData(KEY) ?: 0

  /**
   * Sets [limit] on [indicator]. Does nothing when [indicator] cannot hold user data.
   */
  @JvmStatic
  fun set(indicator: ProgressIndicator, limit: Int) {
    (indicator as? UserDataHolder)?.putUserData(KEY, limit)
  }
}
