// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.images.ImageCollector
import org.jetbrains.intellij.build.images.ImageCollector.IconRobotsData
import org.jetbrains.intellij.build.images.ROBOTS_FILE_NAME
import java.io.File
import java.nio.file.Paths

internal object IconRobotsDataReader {
  @Volatile
  private var iconRobotsData = emptyMap<File, IconRobotsData>()
  private val root = ImageCollector(Paths.get(PathManager.getHomePath()), ignoreSkipTag = false).IconRobotsData()

  fun isSkipped(file: File, repo: File): Boolean {
    val dir = when {
      file.isDirectory -> file
      file.parentFile != null -> file.parentFile
      else -> return false
    }
    val plugins = File(repo, "plugins")
    if (!plugins.exists() ||
        !plugins.isAncestor(file) ||
        !File(dir, ROBOTS_FILE_NAME).exists()) return false
    val path = dir.toPath()
    if (!iconRobotsData.containsKey(dir)) synchronized(this) {
      if (!iconRobotsData.containsKey(dir)) {
        iconRobotsData += dir to root.fork(path, path)
      }
    }
    return iconRobotsData[dir]!!.isSkipped(file.toPath())
  }
}
