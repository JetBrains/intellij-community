// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime

import com.intellij.bootRuntime.bundles.Runtime
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.util.*

private val CONFIG_FILE_EXT = if (!SystemInfo.isWindows) ".jdk" else if (SystemInfo.is64Bit) "64.exe.jdk" else ".exe.jdk"

class RuntimeInstaller {
  companion object {
    fun configFile () : File {
      val selector = PathManager.getPathsSelector()
      val configDir = File(if (selector != null) PathManager.getDefaultConfigPathFor(selector) else PathManager.getConfigPath())
      var exeName: String? = System.getProperty("idea.executable")
      if (exeName == null) exeName = ApplicationNamesInfo.getInstance().productName.toLowerCase(Locale.US)
      return File(configDir, exeName + CONFIG_FILE_EXT)
    }

    fun installRuntimeBundle (bundle: Runtime) {
      configFile().writeText(bundle.installationPath.absolutePath)
    }
  }
}