// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface PluginInstallationCustomization {

  companion object {

    private val LOG = logger<PluginInstallationCustomization>()

    @ApiStatus.ScheduledForRemoval
    @Deprecated("Temporary solution")
    private const val CUSTOMIZE_TAGS_COMPATIBILITY_METHOD = "customizeTagsCompatibility"

    private val EP_NAME = ExtensionPointName.create<PluginInstallationCustomization>("com.intellij.pluginInstallationCustomization")

    @JvmStatic
    fun findPluginInstallationCustomization(plugin: PluginId): PluginInstallationCustomization? {
      val customizations = EP_NAME.extensionList
        .filter { it.pluginId == plugin }
        .sortedBy { it.priority }

      return customizations.lastOrNull()
    }
  }

  val pluginId: PluginId

  val priority: Int
    get() = 0

  fun createLicensePanel(isMarketplace: Boolean, update: Boolean): JComponent?

  fun beforeInstallOrUpdate(update: Boolean)

  fun customizeTags(tags: List<String>): List<String> {
    val method = ReflectionUtil.getMethod(this::class.java, CUSTOMIZE_TAGS_COMPATIBILITY_METHOD, List::class.java) ?: return tags

    return runCatching {
      method.invoke(this, tags) as List<String>
    }.getOrLogException(LOG) ?: tags
  }
}
