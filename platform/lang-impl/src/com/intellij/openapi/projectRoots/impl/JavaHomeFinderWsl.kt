// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.execution.wsl.WSLDistribution
import java.nio.file.Path
import java.nio.file.Paths

class JavaHomeFinderWsl internal constructor(
  private val distribution: WSLDistribution,
  forceEmbeddedJava: Boolean,
  vararg paths: String
) : JavaHomeFinderBasic(forceEmbeddedJava, *paths) {
  override fun checkDefaultLocations(): Set<String> {
    return emptySet()
  }

  override fun scanFolder(folder: Path, includeNestDirs: Boolean, result: MutableCollection<in String>) {
    val path = folder.toString()
    when {
      path.startsWith(distribution.mntRoot) -> return
      path.startsWith(WSLDistribution.UNC_PREFIX) -> super.scanFolder(folder, includeNestDirs, result)
      else -> {
        distribution.getWindowsPath(path)?.let { windowsPath ->
          super.scanFolder(Paths.get(windowsPath), includeNestDirs, result)
        }
      }
    }
  }

  override fun getEnvironmentVariable(name: String): String? {
    return distribution.getEnvironmentVariable(name)
  }

  override fun getPath(): Array<String> {
    return getEnvironmentVariable("PATH")?.split(':')?.toTypedArray() ?: arrayOf()
  }
}