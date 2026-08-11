// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.plugins.PluginManagerCore.findPlugin
import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance(IdeMessagePanelReleaseExceptionsFilter::class.java)

/**
 * Used in releases of IDE products where exception automatic reporting is not supported.
 */
internal class IdeMessagePanelReleaseExceptionsFilter : MessagePoolAdvisor {
  override suspend fun beforeEntryAdded(e: MessagePoolAdvisor.BeforeEntryAddedEvent): Boolean {
    val t = e.message.getThrowable()
    val pluginId = PluginUtil.getInstance().findPluginId(t)
    if (pluginId != null) {
      val plugin = findPlugin(pluginId)
      if (IdeMessagePanel.isBuiltIn(plugin)) {
        LOG.debug("Ignored exception in built-in plugin as automatic reporting is not available")
        return false
      }
    }

    return super.beforeEntryAdded(e)
  }
}
