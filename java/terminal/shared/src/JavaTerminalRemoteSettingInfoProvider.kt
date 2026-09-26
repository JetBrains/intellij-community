// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.shared

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

internal class JavaTerminalRemoteSettingInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> {
    return mapOf(JavaTerminalSettings.COMPONENT_NAME to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend))
  }
}
