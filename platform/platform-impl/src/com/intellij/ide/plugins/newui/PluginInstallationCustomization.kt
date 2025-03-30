// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface PluginInstallationCustomization {

  companion object {

    private val EP_NAME = ExtensionPointName.create<PluginInstallationCustomization>("com.intellij.pluginInstallationCustomization")

    @JvmStatic
    fun findPluginInstallationCustomization(plugin: PluginId): PluginInstallationCustomization? {
      return EP_NAME.extensionList.firstOrNull { it.pluginId == plugin }
    }
  }

  val pluginId: PluginId

  fun createLicensePanel(isMarketplace: Boolean, update: Boolean): JComponent?
  
  fun beforeInstallOrUpdate(update: Boolean)
}


