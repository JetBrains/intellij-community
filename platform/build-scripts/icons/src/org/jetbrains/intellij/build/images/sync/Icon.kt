// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.imageSize
import org.jetbrains.intellij.build.images.isImage
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class Icon(private val file: Path) {
  val isValid by lazy {
    val dimension = dimension ?: return@lazy false
    val pixels = if (file.fileName.toString().contains("@2x")) 64 else 32
    dimension.height <= pixels && dimension.width <= pixels || isPluginLogo()
  }

  private val dimension by lazy {
    if (isImage(file)) {
      try {
        muteStdErr {
          imageSize(file, failOnMalformedImage = false)
        }
      }
      catch (ignore: NoSuchFileException) {
        null
      }
    }
    else null
  }

  private val pluginIcon = PluginLogo.getIconFileName(true)
  private val pluginIconDark = PluginLogo.getIconFileName(false)

  private fun isPluginLogo(): Boolean {
    val dimension = dimension ?: return false
    if (dimension.height == PluginLogo.height() && dimension.width == PluginLogo.width()) {
      val path = file.toAbsolutePath().invariantSeparatorsPathString
      return path.endsWith(pluginIcon) || path.endsWith(pluginIconDark)
    }
    return false
  }

  /**
   * Copied from [com.intellij.ide.plugins.newui.PluginLogo] not to depend on huge intellij.platform.ide.impl module
   */
  private object PluginLogo {
    private const val PLUGIN_ICON = "pluginIcon.svg"
    private const val PLUGIN_ICON_DARK = "pluginIcon_dark.svg"
    private const val PLUGIN_ICON_SIZE = 40
    private const val META_INF = "META-INF/"
    fun getIconFileName(light: Boolean) = META_INF + if (light) PLUGIN_ICON else PLUGIN_ICON_DARK
    fun height() = PLUGIN_ICON_SIZE
    fun width() = PLUGIN_ICON_SIZE
  }
}