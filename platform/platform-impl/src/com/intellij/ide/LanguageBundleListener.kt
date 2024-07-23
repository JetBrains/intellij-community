// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.DynamicBundle
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.text.DateTimeFormatManager

private class LanguageBundleListener : AppLifecycleListener {
  override fun appStarted() {
    LocalizationStateService.getInstance()?.let {
      (it as PersistentStateComponent<*>).initializeComponent()
      it.resetLocaleIfNeeded()
    }
    DynamicBundle.clearCache()
    DateTimeFormatManager.getInstance().resetFormats()
  }
}
