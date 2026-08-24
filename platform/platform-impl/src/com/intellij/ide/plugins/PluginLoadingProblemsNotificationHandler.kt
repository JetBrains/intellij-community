// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface PluginLoadingProblemsNotificationHandler {

  suspend fun getPluginLoadingProblemsForNotification(): PluginLoadingProblemsNotificationData

  suspend fun onEnableClickInNotification()

  suspend fun onDisableClickInNotification()

  companion object {
    private val EP_NAME = ExtensionPointName.create<PluginLoadingProblemsNotificationHandler>("com.intellij.pluginLoadingProblemsNotificationHandler")

    fun getInstances(): List<PluginLoadingProblemsNotificationHandler> = EP_NAME.extensionList
  }
}

@Serializable
@ApiStatus.Internal
data class PluginLoadingProblemsNotificationData(
  val pluginLoadingProblemsForNotification: Collection<@Nls String>,
  val pluginNamesToEnable: Collection<String>,
  val pluginNamesToDisable: Collection<String>
)
