// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.util.system.OS
import com.intellij.util.ui.UnixDesktopEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.APP)
private class OsDataLogger(val coroutineScope: CoroutineScope) {
  @Volatile
  var osInfoAboutString: String? = null

  fun reportLinuxDistro() {
    coroutineScope.launch {
      val osInfo = OS.CURRENT.osInfo as OS.UnixInfo

      var info = ""
      if (osInfo.prettyName != null) {
        info += osInfo.prettyName
      } else {
        info += osInfo.distro ?: "Unknown Distro"
        if (osInfo.release != null) {
          info += " " + osInfo.release
        }
      }
      if (osInfo is OS.LinuxInfo) {
        if (osInfo.isUnderWsl) info += " (in WSL)"
        if (osInfo.glibcVersion != null) info += "; glibc: " + osInfo.glibcVersion
      }
      UnixDesktopEnv.CURRENT?.also { currentEnv ->
        info += "; desktop: " + currentEnv.name
        withContext(Dispatchers.IO) {
          ExecUtil.execAndReadLine(GeneralCommandLine(listOf(currentEnv.versionTool) + currentEnv.versionToolArguments))?.also { line ->
            info += " ($line)"
          }
        }
      }

      logger<OsDataLogger>().info(info)

      osInfoAboutString = info
    }
  }
}

private class OsDataLoggerApplicationInitializedListener : AppLifecycleListener {
  override fun appStarted() {
    if (OS.isGenericUnix()) {
      service<OsDataLogger>().reportLinuxDistro()
    }
  }
}

internal class OsDataLoggerAboutPopupDescriptionProvider : AboutPopupDescriptionProvider {
  override fun getDescription(): @DetailedDescription String? = null

  override fun getExtendedDescription(): @DetailedDescription String? =
    if (OS.isGenericUnix()) service<OsDataLogger>().osInfoAboutString else null
}
