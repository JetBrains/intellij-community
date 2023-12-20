// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.diagnostic.PluginException
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.ResourceUtil
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException

/**
 * Extension point for adding UI themes.
 * Read more about [themes development](https://plugins.jetbrains.com/docs/intellij/theme-structure.html).
 *
 * @author Konstantin Bulenkov
 */
class UIThemeProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<UIThemeProvider> = ExtensionPointName("com.intellij.themeProvider")
  }

  /**
   * Path to `*.theme.json` file
   */
  @Attribute("path")
  @RequiredElement
  @JvmField
  var path: String? = null

  /**
   * Unique theme identifier. For example, MyTheme123
   */
  @Attribute("id")
  @RequiredElement
  @JvmField
  var id: String? = null

  @Deprecated("Please specify parentTheme in JSON")
  @Attribute("parentTheme")
  @JvmField
  var parentTheme: String? = null

  @Attribute("targetUi")
  @JvmField
  var targetUI: TargetUIType = TargetUIType.UNSPECIFIED

  @Throws(IOException::class)
  @Internal
  fun getThemeJson(pluginDescriptor: PluginDescriptor): ByteArray? {
    return ResourceUtil.getResourceAsBytes((path ?: return null).removePrefix("/"), pluginDescriptor.getClassLoader())
  }

  internal fun createTheme(parentTheme: UITheme?,
                           defaultDarkParent: (() -> UITheme?)?,
                           defaultLightParent: (() ->UITheme?)?,
                           pluginDescriptor: PluginDescriptor): UITheme? {
    if (defaultDarkParent != null && id == UiThemeProviderListManager.DEFAULT_DARK_PARENT_THEME) {
      val result = defaultDarkParent()
      if (result?.id == UiThemeProviderListManager.DEFAULT_DARK_PARENT_THEME) {
        return result
      }
    }
    if (defaultLightParent != null && id == UiThemeProviderListManager.DEFAULT_LIGHT_PARENT_THEME) {
      val result = defaultLightParent()
      if (result?.id == UiThemeProviderListManager.DEFAULT_LIGHT_PARENT_THEME) {
        return result
      }
    }

    try {
      val classLoader = pluginDescriptor.getPluginClassLoader() ?: UIThemeProvider::class.java.classLoader
      val data = getThemeJson(pluginDescriptor)
      if (data == null) {
        thisLogger().warn(PluginException(
          "Cannot find theme resource (path=$path, classLoader=$classLoader, pluginDescriptor=$pluginDescriptor)",
          pluginDescriptor.getPluginId()))
        return null
      }

      return UITheme.loadFromJson(
        parentTheme = parentTheme,
        data = data,
        themeId = id!!,
        classLoader = classLoader,
        defaultDarkParent = defaultDarkParent,
        defaultLightParent = defaultLightParent,
        warn = { message, error ->
          thisLogger().warn(PluginException(message, error, pluginDescriptor.pluginId))
        },
      )
    }
    catch (e: Throwable) {
      thisLogger().warn(PluginException("Cannot load UI theme (path=$path, pluginDescriptor=$pluginDescriptor)",
                                        e,
                                        pluginDescriptor.getPluginId()))
      return null
    }
  }
}
