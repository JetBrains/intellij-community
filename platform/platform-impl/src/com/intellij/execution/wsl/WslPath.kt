// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.impl.wsl.WslConstants

data class WslPath(val distributionId: String, val linuxPath: String) {
  val distribution: WSLDistribution by lazy {
    WslDistributionManager.getInstance().getOrCreateDistributionByMsId(distributionId)
  }

  companion object {
    @JvmStatic
    fun parseWindowsUncPath(windowsUncPath: String): WslPath? {
      if (!WSLUtil.isSystemCompatible()) return null
      val path = FileUtil.toSystemDependentName(windowsUncPath)
      if (path.startsWith(WslConstants.UNC_PREFIX)) {
        val slashIndex = path.indexOf('\\', WslConstants.UNC_PREFIX.length)
        if (slashIndex > WslConstants.UNC_PREFIX.length) {
          return WslPath(path.substring(WslConstants.UNC_PREFIX.length, slashIndex),
                         FileUtil.toSystemIndependentName(path.substring(slashIndex)))
        }
      }
      return null
    }

    @JvmStatic
    fun getDistributionByWindowsUncPath(windowsUncPath: String): WSLDistribution? = parseWindowsUncPath(windowsUncPath)?.distribution

    @JvmStatic
    fun isWslUncPath(windowsUncPath: String): Boolean = parseWindowsUncPath(windowsUncPath) != null
  }
}
