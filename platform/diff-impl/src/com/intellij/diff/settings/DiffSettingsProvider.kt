// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

internal class DiffSettingsProvider: RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> {
    return mapOf(DiffSettingsHolder.SETTINGS_KEY to RemoteSettingInfo(RemoteSettingInfo.Direction.OnlyFromBackend))
  }
}