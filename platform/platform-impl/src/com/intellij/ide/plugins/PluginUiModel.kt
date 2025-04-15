// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import java.text.DecimalFormat

/**
 * A lightweight model for representing plugin information in the UI.
 * This interface contains only the subset of plugin metadata needed for display purposes.
 */
interface PluginUiModel {
  val pluginId: PluginId

  val isIncompatibleWithCurrentOs: Boolean

  val canBeEnabled: Boolean

  val name: String

  val source: PluginSource

  /**
   * Returns true if the plugin requires an upgrade to a commercial IDE.
   */
  val requiresUpgrade: Boolean

  val tags: List<String>

  val isBundled: Boolean

  @get:NlsSafe
  val version: String?

  @get:NlsSafe
  val vendor: String?

  @get:NlsSafe
  val organization: String?

  val downloads: String?

  val rating: String?

  val isLicenseOptional: Boolean

  /**
   * Java compatibility method. Going to be removed after refactoring is done.
   */
  fun getDescriptor(): IdeaPluginDescriptor = this.getPluginDescriptor()
}

fun PluginUiModel.getPluginDescriptor(): IdeaPluginDescriptor {
  if (this is PluginUiModelAdapter) return pluginDescriptor
  throw IllegalStateException("PluginUiModelAdapter expected")
}

enum class PluginSource {
  LOCAL, REMOTE
}

private val K_FORMAT = DecimalFormat("###.#K")
private val M_FORMAT = DecimalFormat("###.#M")

@NlsSafe
fun PluginUiModel.presentableRating(): String? {
  val rating = this.rating ?: return null
  if (rating.isBlank()) return null

  try {
    val value = rating.toDouble()
    if (value > 0) return rating.removeSuffix(".0")
  }
  catch (_: NumberFormatException) { }
  return null
}

@NlsSafe
fun PluginUiModel.presentableDownloads(): String? {
  val downloads = this.downloads ?: return null
  if (downloads.isBlank()) return null

  try {
    val value = downloads.toLong()
    return when {
      value <= 1000 -> value.toString()
      value < 1000000 -> K_FORMAT.format(value / 1000.0)
      else -> M_FORMAT.format(value / 1000000.0)
    }
  }
  catch (_: NumberFormatException) { }
  return null
}
