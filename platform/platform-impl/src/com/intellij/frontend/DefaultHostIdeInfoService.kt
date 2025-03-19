// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.frontend

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.util.system.OS

internal class DefaultHostIdeInfoService : HostIdeInfoService {
  override fun getHostInfo(): HostInfo {
    return HostInfo(productCode = ApplicationInfo.getInstance().build.productCode,
                    osName = OS.CURRENT.name,
                    osVersion = OS.CURRENT.version)
  }
}
