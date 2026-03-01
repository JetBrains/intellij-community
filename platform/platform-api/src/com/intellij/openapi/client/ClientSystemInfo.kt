// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Provides information about operating system, just like [SystemInfo], but for the current client (in terms of
 * [com.intellij.codeWithMe.ClientId.current])
 */
@ApiStatus.Experimental
class ClientSystemInfo private constructor() {
  companion object {
    @JvmStatic
    fun isMac(): Boolean {
      return getInstance()?.macClient ?: SystemInfo.isMac
    }

    @JvmStatic
    fun isWindows(): Boolean {
      return getInstance()?.windowsClient ?: SystemInfo.isWindows
    }

    @JvmStatic
    fun isWaylandToolkit(): Boolean {
      return getInstance()?.waylandToolkitClient ?: StartupUiUtil.isWaylandToolkit()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun getInstance(): ClientSystemInfo? {
      return ApplicationManager.getApplication()?.currentSessionOrNull?.takeIf { it.isRemote }?.getUserData(CLIENT_INFO_KEY)
    }

    private val CLIENT_INFO_KEY = KeyWithDefaultValue.create("ClientSystemInfo") { ClientSystemInfo() }
  }

  @ApiStatus.Internal
  var macClient: Boolean? = null

  @ApiStatus.Internal
  var windowsClient: Boolean? = null

  @ApiStatus.Internal
  var waylandToolkitClient: Boolean? = null

  @ApiStatus.Internal
  var windowRoundedCornersManagerAvailable: Boolean? = null
}