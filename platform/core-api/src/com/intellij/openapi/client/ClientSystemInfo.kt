// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus

/**
 * Provides information about operating system, just like [SystemInfo], but for the current client (in terms of [ClientId.current])
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

    @ApiStatus.Internal
    fun getInstance(): ClientSystemInfo? {
      return ApplicationManager.getApplication()?.currentSessionOrNull?.takeIf { it.isRemote }?.getUserData(CLIENT_INFO_KEY)
    }

    private val CLIENT_INFO_KEY = KeyWithDefaultValue.create("ClientSystemInfo") { ClientSystemInfo() }
  }

  var macClient: Boolean? = null
  var windowsClient: Boolean? = null
}