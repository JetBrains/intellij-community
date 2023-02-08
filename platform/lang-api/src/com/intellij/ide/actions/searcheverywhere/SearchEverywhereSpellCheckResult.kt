// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.util.registry.Registry
import com.intellij.util.applyIf
import org.jetbrains.annotations.ApiStatus

/**
 * Result provided by the [SearchEverywhereSpellingCorrector], which can either be
 * [SearchEverywhereSpellCheckResult.Correction] where the suggested query correction along with confidence is specified, or
 * [SearchEverywhereSpellCheckResult.NoCorrection] singleton for cases where query is already correct or suggestion could not be provided
 */
@ApiStatus.Internal
sealed interface SearchEverywhereSpellCheckResult {
  data class Correction(val correction: String, val confidence: Double): SearchEverywhereSpellCheckResult {
    val presentationText: String = "Do you mean '$correction'?"
      .applyIf(showConfidence) {
        "$this (p=%.2f)".format(confidence)
      }

    private val showConfidence: Boolean
      get() = Registry.`is`("search.everywhere.ml.typos.show.confidence", false)
  }

  object NoCorrection : SearchEverywhereSpellCheckResult
}
