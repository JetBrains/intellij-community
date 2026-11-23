// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated

/**
 * Drops the cached settings objects from [InlayHintsSettings] after an unload, so that a settings class of an unloaded module is not kept.
 */
internal class InlayHintsSettingsPluginListener : DynamicPluginListener {
  override fun pluginsUnloaded() {
    ApplicationManager.getApplication().serviceIfCreated<InlayHintsSettings>()?.forgetCachedSettings()
  }
}
