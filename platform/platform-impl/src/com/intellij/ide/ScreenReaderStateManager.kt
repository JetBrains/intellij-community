// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.APP)
internal class ScreenReaderStateManager(coroutineScope: CoroutineScope) {
  init {
    coroutineScope.launch {
      apply()

      ApplicationManager.getApplication().serviceAsync<GeneralSettings>().propertyChangedFlow.collect {
        if (it == GeneralSettings.PropertyNames.supportScreenReaders) {
          apply()
        }
      }
    }
  }

  suspend fun apply() {
    withContext(Dispatchers.EDT) {
      ScreenReader.setActive(GeneralSettings.getInstance().isSupportScreenReaders)
    }
  }
}