// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.l10n

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
class LanguageBundleEP : PluginAware {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<LanguageBundleEP> = ExtensionPointName("com.intellij.languageBundle")
  }

  @Attribute("locale")
  var locale: String = Locale.ENGLISH.language
  var pluginDescriptor: PluginDescriptor? = null

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }
}