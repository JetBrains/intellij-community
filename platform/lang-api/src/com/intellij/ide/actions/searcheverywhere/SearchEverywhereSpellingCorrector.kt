// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface SearchEverywhereSpellingCorrector {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SearchEverywhereSpellingCorrectorFactory>("com.intellij.searchEverywhereSpellingCorrector")

    // Cache the first matching corrector so subsequent calls don’t re-create it.
    private val cachedInstance: SearchEverywhereSpellingCorrector? by lazy {
      EP_NAME.extensionList.firstOrNull { it.isAvailable() }?.create()
    }

    @JvmStatic
    fun getInstance(): SearchEverywhereSpellingCorrector? {
      return cachedInstance
    }
  }

  fun isAvailableInTab(tabId: String): Boolean

  fun checkSpellingOf(query: String): SearchEverywhereSpellCheckResult

  fun getAllCorrections(query: String, maxCorrections: Int): List<SearchEverywhereSpellCheckResult.Correction>
}

@Internal
interface SearchEverywhereSpellingCorrectorFactory {
  fun isAvailable(): Boolean

  fun create(): SearchEverywhereSpellingCorrector
}