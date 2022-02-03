// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.impl.wsl.WslConstants

data class WslPath(val distributionId: String, val linuxPath: String) {
  val distribution: WSLDistribution by lazy {
    WslDistributionManager.getInstance().getOrCreateDistributionByMsId(distributionId)
  }

  companion object {
    @JvmStatic
    fun parseWindowsUncPath(windowsUncPath: String): WslPath? {
      if (!WSLUtil.isSystemCompatible()) return null
      var path = FileUtil.toSystemDependentName(windowsUncPath)
      if (!path.startsWith(WslConstants.UNC_PREFIX)) return null
      path = StringUtil.trimStart(path, WslConstants.UNC_PREFIX)
      val index = path.indexOf('\\')
      if (index <= 0) return null
      return WslPath(path.substring(0, index), FileUtil.toSystemIndependentName(path.substring(index)))
    }

    @JvmStatic
    fun getDistributionByWindowsUncPath(windowsUncPath: String): WSLDistribution? = parseWindowsUncPath(windowsUncPath)?.distribution

    @JvmStatic
    fun isWslUncPath(windowsUncPath: String): Boolean = parseWindowsUncPath(windowsUncPath) != null
  }
}
