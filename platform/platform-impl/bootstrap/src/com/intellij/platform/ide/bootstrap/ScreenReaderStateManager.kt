// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.ui.RawSwingDispatcher
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.APP)
internal class ScreenReaderStateManager(coroutineScope: CoroutineScope) {
  init {
    coroutineScope.launch {
      val generalSettings = serviceAsync<GeneralSettings>()
      apply(generalSettings)
      generalSettings.propertyChangedFlow.collect {
        if (it == GeneralSettings.PropertyNames.supportScreenReaders) {
          apply(generalSettings)
        }
      }
    }
  }

  suspend fun apply(generalSettings: GeneralSettings) {
    // https://youtrack.jetbrains.com/issue/IDEA-332882 - use RawSwingDispatcher
    withContext(RawSwingDispatcher) {
      ScreenReader.setActive(generalSettings.isSupportScreenReaders)
    }
  }
}