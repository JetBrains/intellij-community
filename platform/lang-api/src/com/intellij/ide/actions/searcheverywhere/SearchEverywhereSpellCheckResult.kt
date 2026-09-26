// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.DynamicBundle
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.applyIf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Result provided by the [SearchEverywhereSpellingCorrector], which can either be
 * [SearchEverywhereSpellCheckResult.Correction] where the suggested query correction along with confidence is specified, or
 * [SearchEverywhereSpellCheckResult.NoCorrection] singleton for cases where query is already correct or suggestion could not be provided
 */
@ApiStatus.Experimental
sealed interface SearchEverywhereSpellCheckResult {
  data class Correction(val correction: String, val confidence: Double) : SearchEverywhereSpellCheckResult {
    val presentationText: @Nls String = TypoFixingBundle.message("search.everywhere.typo.suggestion", correction)
      .applyIf(showConfidence) {
        @Suppress("HardCodedStringLiteral")
        "$this (p=%.2f)".format(confidence)
      }

    private val showConfidence: Boolean
      get() = Registry.`is`("search.everywhere.ml.typos.show.confidence", false)
  }

  object NoCorrection : SearchEverywhereSpellCheckResult
}

private const val TYPO_FIXING_BUNDLE_PATH = "messages.TypoFixingBundle"

private object TypoFixingBundle {
  private val instance = DynamicBundle(TypoFixingBundle::class.java, TYPO_FIXING_BUNDLE_PATH)

  fun message(key: @PropertyKey(resourceBundle = TYPO_FIXING_BUNDLE_PATH) String, vararg params: Any): @Nls String {
    return instance.getMessage(key, *params)
  }
}
