// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.IconRobotsData
import org.jetbrains.intellij.build.images.ImageSyncFlags
import org.jetbrains.intellij.build.images.ROBOTS_FILE_NAME
import java.io.File

internal object IconRobotsDataReader {
  @Volatile
  private var iconRobotsData = emptyMap<File, IconRobotsData>()
  private val root = IconRobotsData(null, ignoreSkipTag = false, usedIconsRobots = null)

  private fun readIconRobotsData(file: File, block: ImageSyncFlags.() -> Boolean): Boolean {
    val robotFile = findRobotsFileName(file) ?: return false
    if (!iconRobotsData.containsKey(robotFile)) {
      synchronized(this) {
        if (!iconRobotsData.containsKey(robotFile)) {
          iconRobotsData = iconRobotsData.plus((robotFile to root.fork(robotFile.toPath(), robotFile.toPath())))
        }
      }
    }
    return iconRobotsData.getValue(robotFile).getImageSyncFlags(file.toPath()).block()
  }

  private fun findRobotsFileName(file: File): File? {
    if (file.isDirectory && File(file, ROBOTS_FILE_NAME).exists()) {
      return file
    }
    else {
      return file.parentFile?.let(::findRobotsFileName)
    }
  }

  fun isSyncSkipped(file: File): Boolean {
    return readIconRobotsData(file) {
      skipSync
    }
  }

  fun isSyncForced(file: File): Boolean {
    return readIconRobotsData(file) {
      forceSync
    }
  }
}
