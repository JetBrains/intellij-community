// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.UnixUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.APP)
private class OsDataLogger(val coroutineScope: CoroutineScope) {
  internal var osInfoAboutString: String? = null

  fun reportLinuxDistro() {
    coroutineScope.launch {
      if (SystemInfo.isLinux || SystemInfo.isFreeBSD) {
        val osInfo = UnixUtil.getOsInfo()

        val name = osInfo.prettyName
                   ?: (osInfo.distro ?: "Unknown Distro").appendNotBlank(" ", osInfo.release)
        val info = name
          .appendNotBlank(" ", if (osInfo.isUnderWsl) "(in WSL)" else null)
          .appendNotBlank("; glibc: ", osInfo.glibcVersion?.toString())
        logger<OsDataLogger>().info(info)

        osInfoAboutString = info
      }
    }
  }

  private fun String.appendNotBlank(delimiter: String?, value: String?): String {
    if (value.isNullOrBlank()) return this
    return this + delimiter + value
  }
}

internal class OsDataLoggerApplicationInitializedListener : ApplicationInitializedListener {
  override suspend fun execute() {
    service<OsDataLogger>().reportLinuxDistro()
  }
}

internal class OsDataLoggerAboutPopupDescriptionProvider : AboutPopupDescriptionProvider {
  override fun getDescription(): @DetailedDescription String? = null

  override fun getExtendedDescription(): @DetailedDescription String? {
    return service<OsDataLogger>().osInfoAboutString
  }
}
