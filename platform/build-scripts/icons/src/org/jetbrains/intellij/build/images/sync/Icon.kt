// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.ide.plugins.newui.PluginLogo
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.intellij.build.images.imageSize
import org.jetbrains.intellij.build.images.isImage
import java.io.File

internal class Icon(private val file: File) {
  val isValid by lazy {
    if (dimension != null) {
      val pixels = if (file.name.contains("@2x")) 64 else 32
      dimension!!.height <= pixels && dimension!!.width <= pixels || isPluginLogo()
    }
    else false
  }

  private val dimension by lazy {
    if (file.exists() && isImage(file.toPath())) {
      try {
        muteStdErr {
          imageSize(file.toPath())
        }
      }
      catch (e: Exception) {
        log("WARNING: $file: ${e.message}")
        null
      }
    }
    else null
  }

  private val pluginIcon = PluginLogo.getIconFileName(true)
  private val pluginIconDark = PluginLogo.getIconFileName(false)
  private fun isPluginLogo() = dimension?.let {
    it.height == PluginLogo.height() && it.width == PluginLogo.width()
  } == true && with(file.canonicalPath.let(FileUtil::toSystemIndependentName)) {
    endsWith(pluginIcon) || endsWith(pluginIconDark)
  }
}