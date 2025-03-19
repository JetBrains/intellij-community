// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.settings

import com.intellij.openapi.extensions.ExtensionPointName


/**
 * Interface for providing settings that must be synchronized between
 * the host and a guest (CodeWithMe and Remote Development).
 *
 * See [DefaultRemoteSettingInfoProvider] as an example.
 *
 * **Important note:** this provider will be used **both** on backend and frontend,
 * so all implementations must be registered either in **both** backend and frontend modules,
 * or in some common module, that is loaded on both sides (e.g. `intellij.platform.split`).
 */
interface RemoteSettingInfoProvider {
  /**
   * Returns a map of settings that should be synchronized between the host and a guest.
   *
   * Each entry is `(settingKey: String, info: RemoteSettingInfo)`, where `settingKey` can be:
   * 1. Name of the PersistentStateComponent, e.g. `EditorSettings`.
   * 2. Name of a component + name of the field, e.g. `GeneralSettings.confirmOpenNewProject2`.
   *
   * A couple notes about a field's name:
   * * It must be the name from a "state" class, not from a component itself. For example,
   *   there is `GeneralSettings.confirmOpenNewProject` and `GeneralSettingsState.confirmOpenNewProject2`.
   *   You must use the second one, but combined with the component's name: `GeneralSettings.confirmOpenNewProject2`.
   * * Sometimes this name can be specified explicitly, like `UISettingsState.consoleCommandHistoryLimit`
   *   has an explicit annotation `@get:OptionTag("CONSOLE_COMMAND_HISTORY_LIMIT")`, so the correct
   *   setting key would be `UISettings.CONSOLE_COMMAND_HISTORY_LIMIT`.
   *
   * **Note**: This method is called only on startup or when the list of extensions is changed.
   *
   * @see com.jetbrains.rd.platform.codeWithMe.settings.DefaultRemoteSettingInfoProvider
   * @see RemoteSettingInfo
   */
  fun getRemoteSettingsInfo(): Map<String, RemoteSettingInfo>

  /**
   * Returns a map from `$pluginId.$componentName` to remote plugin id.
   *
   * Sometimes the same plugin with settings has different pluginId-s on a frontend and a backend.
   * Imagine a plugin has `com.intellij` pluginId on a backend and `com.intellij.plugin` on a frontend,
   * and it has a single settings component with a name `PluginSettings`.
   * Here is an example implementation of [getPluginIdMapping] for this situation:
   * ```kotlin
   * fun getPluginIdMapping(endpoint: Endpoint) = when (endpoint) {
   *   Endpoint.Backend -> mapOf("com.intellij.PluginSettings" to "com.intellij.plugin")
   *   else -> mapOf("com.intellij.plugin.PluginSettings" to "com.intellij")
   * }
   * ```
   *
   * @param endpoint local endpoint where this method is called
   */
  fun getPluginIdMapping(endpoint: RemoteSettingInfo.Endpoint): Map<String, String> = emptyMap()

  companion object {
    val EP_NAME = ExtensionPointName.create<RemoteSettingInfoProvider>("com.intellij.rdct.remoteSettingProvider")
  }
}