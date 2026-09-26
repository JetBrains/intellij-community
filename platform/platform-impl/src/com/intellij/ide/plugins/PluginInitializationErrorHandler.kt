// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface PluginInitializationErrorHandler {

  suspend fun getPluginInitializationErrors(): PluginInitializationErrors

  suspend fun enableDeferredPlugins()

  suspend fun disableDeferredPlugins()

  companion object {
    private val EP_NAME = ExtensionPointName.create<PluginInitializationErrorHandler>("com.intellij.pluginInitializationErrorHandler")

    fun getInstances(): List<PluginInitializationErrorHandler> = EP_NAME.extensionList
  }
}

@Serializable
@ApiStatus.Internal
data class PluginInitializationErrors(
  val pluginErrors: Collection<@Nls String>,
  val pluginNamesToEnable: Collection<String>,
  val pluginNamesToDisable: Collection<String>
)
