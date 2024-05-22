// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface LocalizationStateService {
  companion object {
    fun getInstance(): LocalizationStateService? {
        if (!LoadingState.COMPONENTS_REGISTERED.isOccurred) {
          return null
        }
        return ApplicationManager.getApplication().getService(
          LocalizationStateService::class.java)
      }
  }

  fun getLocalizationState(): LocalizationState
}

@Internal
data class LocalizationState(
  var selectedLocale: String = "en",
  var lastSelectedLocale: String = "en"
)