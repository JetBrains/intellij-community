// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.images.ImageCollector
import org.jetbrains.intellij.build.images.ImageCollector.IconRobotsData
import org.jetbrains.intellij.build.images.ImageSyncFlags
import org.jetbrains.intellij.build.images.ROBOTS_FILE_NAME
import java.io.File
import java.nio.file.Paths

internal object IconRobotsDataReader {
  @Volatile
  private var iconRobotsData = emptyMap<File, IconRobotsData>()
  private val root = ImageCollector(Paths.get(PathManager.getHomePath()), ignoreSkipTag = false).IconRobotsData()
  private fun readIconRobotsData(file: File, block: ImageSyncFlags.() -> Boolean): Boolean {
    val dir = when {
      file.isDirectory -> file
      file.parentFile != null -> file.parentFile
      else -> return false
    }
    if (!File(dir, ROBOTS_FILE_NAME).exists()) return false
    val path = dir.toPath()
    if (!iconRobotsData.containsKey(dir)) synchronized(this) {
      if (!iconRobotsData.containsKey(dir)) {
        iconRobotsData += dir to root.fork(path, path)
      }
    }
    return iconRobotsData[dir]!!.getImageSyncFlags(file.toPath()).block()
  }

  fun isSyncSkipped(file: File) = readIconRobotsData(file) {
    skipSync
  }

  fun isSyncForced(file: File) = readIconRobotsData(file) {
    forceSync
  }
}
