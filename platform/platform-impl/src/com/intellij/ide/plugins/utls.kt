// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PluginUtilsKt")
@file:ApiStatus.Internal

package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.environment.EnvironmentService
import com.intellij.ide.plugins.newui.LicensePanel
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.Tags
import com.intellij.internal.inspector.PropertyBean
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.Ref
import com.intellij.ui.LicensingFacade
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus

@IntellijInternalApi
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

@IntellijInternalApi
fun getUiInspectorContextFor(selectedPlugin: PluginUiModel): List<PropertyBean> {
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

fun IdeaPluginDescriptor.getTags(): List<String> {
  var tags: MutableList<String>? = null
  val productCode = getProductCode()

  if (this is PluginNode) {
    tags = this.tags

    if (productCode != null) {
      if (LicensePanel.isEA2Product(productCode)) {
        if (tags != null && tags.contains(Tags.Paid.name)) {
          tags = ArrayList(tags)
          tags.remove(Tags.Paid.name)
        }
      }
      else if (tags == null) {
        return listOf(Tags.Paid.name)
      }
    }
  }
  else if (productCode != null && !this.isBundled && !LicensePanel.isEA2Product(productCode)) {
    val instance = LicensingFacade.getInstance()
    if (instance != null) {
      val stamp = instance.getConfirmationStamp(productCode)
      if (stamp != null) {
        return listOf(if (stamp.startsWith("eval:")) Tags.Trial.name else Tags.Purchased.name)
      }
    }
    return if (isLicenseOptional()) listOf(Tags.Freemium.name) else listOf(Tags.Paid.name)
  }
  if (ContainerUtil.isEmpty(tags)) {
    return mutableListOf()
  }

  if (tags!!.size > 1) {
    tags = ArrayList(tags)
    if (tags.remove(Tags.EAP.name)) {
      tags.add(0, Tags.EAP.name)
    }
    if (tags.remove(Tags.Paid.name)) {
      tags.add(0, Tags.Paid.name)
    }
    if (tags.remove(Tags.Freemium.name)) {
      tags.add(0, Tags.Freemium.name)
    }
  }

  return tags
}