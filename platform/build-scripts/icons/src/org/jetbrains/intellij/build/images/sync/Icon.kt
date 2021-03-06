// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.ide.plugins.newui.PluginLogo
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.intellij.build.images.imageSize
import org.jetbrains.intellij.build.images.isImage
import java.nio.file.NoSuchFileException
import java.nio.file.Path

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
      val path = file.toAbsolutePath().systemIndependentPath
      return path.endsWith(pluginIcon) || path.endsWith(pluginIconDark)
    }
    return false
  }
}