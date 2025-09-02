// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
object PluginCardOverrides {
  private val LOG get() = logger<PluginCardOverrides>()

  private fun isCommunity(): Boolean {
    try {
      val ideCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
      return ideCode == "IC" || ideCode == "PC"
    } catch (e: RuntimeException) {
      // do not fail unit tests when there is no app >_<
      val msg = e.message ?: throw e
      if (msg.contains("Resource not found") && msg.contains("idea/ApplicationInfo.xml")) {
        if (!unitTestIsCommunityDespammer) {
          LOG.warn("failed to read application info, are we in unit tests?", e)
          unitTestIsCommunityDespammer = true
        }
        return false
      } else {
        throw e
      }
    }
  }

  fun getNameOverride(id: PluginId): String? {
    if (isCommunity()) {
      CE_PLUGIN_CARDS[id.idString]?.let {
        return it.title
      }
    }
    return null
  }

  fun getDescriptionOverride(id: PluginId): @Nls String? {
    if (isCommunity()) {
      CE_PLUGIN_CARDS[id.idString]?.let {
        return it.description
      }
    }
    return null
  }

  private var unitTestIsCommunityDespammer = false

  private data class PluginCardInfo(val title: String, val description: @Nls String)

  @Suppress("HardCodedStringLiteral")
  private val CE_PLUGIN_CARDS: Map<String, PluginCardInfo> = mapOf(
    "org.jetbrains.completion.full.line" to PluginCardInfo(
      "AI Promo",
      "Provides an easy way to install AI assistant to your IDE"
    )
  )
}