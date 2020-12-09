// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.images.sync

import org.jetbrains.intellij.build.images.IconRobotsData
import org.jetbrains.intellij.build.images.ImageSyncFlags
import org.jetbrains.intellij.build.images.ROBOTS_FILE_NAME
import java.nio.file.Files
import java.nio.file.Path

internal object IconRobotsDataReader {
  @Volatile
  private var iconRobotsData = emptyMap<Path, IconRobotsData>()
  private val root = IconRobotsData(null, ignoreSkipTag = false, usedIconsRobots = null)

  private fun readIconRobotsData(file: Path, block: ImageSyncFlags.() -> Boolean): Boolean {
    val robotFile = findRobotsFileName(file) ?: return false
    if (!iconRobotsData.containsKey(robotFile)) {
      synchronized(this) {
        if (!iconRobotsData.containsKey(robotFile)) {
          iconRobotsData = iconRobotsData.plus((robotFile to root.fork(robotFile, robotFile)))
        }
      }
    }
    return iconRobotsData.getValue(robotFile).getImageSyncFlags(file).block()
  }

  private fun findRobotsFileName(file: Path): Path? {
    if (Files.isDirectory(file) && Files.exists(file.resolve(ROBOTS_FILE_NAME))) {
      return file
    }
    else {
      return file.parent?.let(::findRobotsFileName)
    }
  }

  fun isSyncSkipped(file: Path): Boolean {
    return readIconRobotsData(file) {
      skipSync
    }
  }

  fun isSyncForced(file: Path): Boolean {
    return readIconRobotsData(file) {
      forceSync
    }
  }
}
