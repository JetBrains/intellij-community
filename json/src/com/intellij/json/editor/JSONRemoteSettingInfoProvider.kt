package com.intellij.json.editor;

import com.intellij.ide.settings.RemoteSettingInfo
import com.intellij.ide.settings.RemoteSettingInfoProvider

class JSONRemoteSettingInfoProvider : RemoteSettingInfoProvider {
  override fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo> = mapOf(
    "JsonEditorOptions" to RemoteSettingInfo(RemoteSettingInfo.Direction.InitialFromFrontend)
  )
}