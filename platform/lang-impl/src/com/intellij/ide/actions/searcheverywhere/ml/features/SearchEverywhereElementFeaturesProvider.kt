// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import kotlin.math.round

internal abstract class SearchEverywhereElementFeaturesProvider {
  abstract fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  queryLength: Int,
                                  localSummary: ActionsLocalSummary,
                                  globalSummary: ActionsGlobalSummaryManager): Map<String, Any>

  protected fun withUpperBound(value: Int): Int {
    if (value > 100) return 101
    return value
  }

  protected fun roundDouble(value: Double): Double {
    if (!value.isFinite()) return -1.0
    return round(value * 100000) / 100000
  }
}