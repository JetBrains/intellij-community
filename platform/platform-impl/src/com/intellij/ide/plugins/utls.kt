// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PluginUtilsKt")
@file:ApiStatus.Internal

package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.environment.EnvironmentService
import com.intellij.internal.inspector.PropertyBean
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import org.jetbrains.annotations.ApiStatus

fun getEnableDisabledPluginsDependentConfirmationData(): Int? {
  val ref: Ref<Int?> = Ref(null)
  val exceptionRef: Ref<Exception?> = Ref(null)
  ProgressManager.getInstance().runProcessWithProgressSynchronously(
    {
      runBlockingCancellable {
        try {
          val keyResult = serviceAsync<EnvironmentService>()
            .getEnvironmentValue(
              PluginEnvironmentKeyProvider.Keys.ENABLE_DISABLED_DEPENDENT_PLUGINS)
          when (keyResult) {
            null -> ref.set(null)
            "all" -> ref.set(Messages.YES)
            "updated" -> ref.set(Messages.NO)
            "none" -> ref.set(Messages.CANCEL)
            else -> {
              logger<PluginManagerMain>().error(
                "Unknown value for key ${PluginEnvironmentKeyProvider.Keys.ENABLE_DISABLED_DEPENDENT_PLUGINS}")
            }
          }
        }
        catch (e: Exception) {
          exceptionRef.set(e)
        }
      }
    }, IdeBundle.message("dialog.title.fetching.predefined.settings.for.disabled.plugins"), true, null)
  exceptionRef.get()?.let { throw it }
  return ref.get()
}

fun getUiInspectorContextFor(selectedPlugin: IdeaPluginDescriptor): List<PropertyBean> {
  val result = mutableListOf<PropertyBean>()
  result.add(PropertyBean("Plugin ID", selectedPlugin.pluginId, true))

  result.add(PropertyBean("Plugin Dependencies",
                          selectedPlugin.dependencies.filter { !it.isOptional }
                            .joinToString(", ") { it.pluginId.idString },
                          true))
  result.add(PropertyBean("Plugin Dependencies (optional)",
                          selectedPlugin.dependencies.filter { it.isOptional }
                            .joinToString(", ") { it.pluginId.idString },
                          true))

  result.add(PropertyBean("Plugin Reverse Dependencies",
                          PluginManager.getPlugins()
                            .filter { plugin -> plugin.dependencies.any { !it.isOptional && it.pluginId == selectedPlugin.pluginId } }
                            .joinToString(", ") { it.pluginId.idString },
                          true))
  result.add(PropertyBean("Plugin Reverse Dependencies (optional)",
                          PluginManager.getPlugins()
                            .filter { plugin -> plugin.dependencies.any { it.isOptional && it.pluginId == selectedPlugin.pluginId } }
                            .joinToString(", ") { it.pluginId.idString },
                          true))
  return result
}