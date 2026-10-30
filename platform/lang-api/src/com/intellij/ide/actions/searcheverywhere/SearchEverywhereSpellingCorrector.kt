// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

@Internal
interface SearchEverywhereSpellingCorrector {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SearchEverywhereSpellingCorrectorFactory>("com.intellij.searchEverywhereSpellingCorrector")

    /**
     * Returns the corrector of the first available factory, or `null` when no factory is available.
     *
     * The extension point keeps the corrector until its extensions change, so a call does not create it again.
     */
    @JvmStatic
    fun getInstance(): SearchEverywhereSpellingCorrector? {
      return EP_NAME.computeIfAbsent(SearchEverywhereSpellingCorrector::class.java) {
        Optional.ofNullable(EP_NAME.extensionList.firstOrNull { it.isAvailable() }?.create())
      }.getOrNull()
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