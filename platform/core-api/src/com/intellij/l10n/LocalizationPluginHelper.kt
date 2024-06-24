// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.l10n

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface LocalizationPluginHelper {
  companion object {
    fun getInstance(): LocalizationPluginHelper? {
      if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
        return null
      }
      return ApplicationManager.getApplication().getService(LocalizationPluginHelper::class.java)
    }
  }

  fun isInactiveLocalizationPlugin(descriptor: IdeaPluginDescriptor): Boolean
}