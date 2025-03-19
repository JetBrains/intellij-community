// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

private class JavaDebuggerSettingsProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo() =
    mapOf("DebuggerSettings" to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend))

  /**
   * DebuggerSettings are located in different plugins in monolith and frontend, so we have to map them.
   * * com.intellij.java in monolith
   * * com.intellij.java.frontend in frontend
   */
  override fun getPluginIdMapping(endpoint: RemoteSettingInfo.Endpoint) = when (endpoint) {
    RemoteSettingInfo.Endpoint.Backend -> mapOf("com.intellij.java.DebuggerSettings" to "com.intellij.java.frontend")
    else -> mapOf("com.intellij.java.frontend.DebuggerSettings" to "com.intellij.java")
  }
}
