// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete.ranking

import org.jetbrains.annotations.ApiStatus

/**
 * Listens to the ranking process of [com.intellij.platform.ml.impl.turboComplete.SuggestionGenerator]
 *
 * The same instance of the listener used during all the application lifetime.
 * The callbacks are called in the order of their declaration
 */
@ApiStatus.Internal
interface KindRankingListener {
  /**
   * The ranking process started
   */
  fun onRankingStarted() {}

  /**
   * The kinds were ranked
   */
  fun onRanked(ranked: List<RankedKind>) {}

  /**
   * The ranking process finished
   */
  fun onRankingFinished() {}
}