// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.l10n.LocalizationUtil
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object LocalizationPluginHelper {


  /**
   * Checks if the given [IdeaPluginDescriptor] is a localization plugin
   * and whether its locale is different from the selected locale.
   *
   * @param descriptor the descriptor of the plugin to be checked
   * @return `true` if the plugin is a localization plugin and its locale
   *         equals to the selected locale; `false` otherwise
   */
  fun isActiveLocalizationPlugin(descriptor: IdeaPluginDescriptor, languageTag: String? = null): Boolean {
    if (descriptor !is IdeaPluginDescriptorImpl) return false
    val extensionPoints = descriptor.extensions
    val epName = "com.intellij.languageBundle"
    if (extensionPoints.containsKey(epName)) {
      val locale = extensionPoints[epName]?.firstOrNull()?.element?.attributes?.get("locale")
      val selectedLocale = languageTag ?: LocalizationUtil.getLocale().toLanguageTag()
      return selectedLocale == locale
    }
    return false
  }
}