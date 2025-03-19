// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RetrievingService")

package com.intellij.ide.ui.laf

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.idea.AppMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private class LafAndEditorColorSchemeDynamicPluginListener : DynamicPluginListener {
  override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    service<LafDynamicPluginManager>().pluginLoaded()
  }

  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    service<LafDynamicPluginManager>().beforePluginUnload(isUpdate)
  }

  override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    service<LafDynamicPluginManager>().reloadColorSchemesAndApplyScheduledLaF()
  }
}

@Service
internal class LafDynamicPluginManager(private val coroutineScope: CoroutineScope) {
  private var isUpdatingPlugin = false
  private var themeIdBeforePluginUpdate: String? = null

  // access only from EDT
  private var scheduledLaF: UIThemeLookAndFeelInfo? = null

  fun reloadColorSchemesAndApplyScheduledLaF() {
    serviceIfCreated<EditorColorsManager>()?.reloadKeepingActiveScheme()
    applyScheduledLaF()
  }

  fun createUiThemeEpListener(manager: LafManagerImpl): ExtensionPointListener<UIThemeProvider> {
    return object : ExtensionPointListener<UIThemeProvider> {
      override fun extensionAdded(extension: UIThemeProvider, pluginDescriptor: PluginDescriptor) {
        val newItem = UiThemeProviderListManager.getInstance().themeProviderAdded(extension, pluginDescriptor) ?: return
        manager.updateLafComboboxModel()

        // when updating a theme plugin that doesn't provide the current theme, don't select any of its themes as current
        if (!AppMode.isRemoteDevHost() && !manager.autodetect && (!isUpdatingPlugin || newItem.id == themeIdBeforePluginUpdate)) {
          scheduleLafChange(newItem.theme.get())
        }
      }

      override fun extensionRemoved(extension: UIThemeProvider, pluginDescriptor: PluginDescriptor) {
        val oldLaF = UiThemeProviderListManager.getInstance().themeProviderRemoved(extension) ?: return
        oldLaF.theme.setProviderClassLoader(null)
        val isDark = oldLaF.isDark
        val defaultLaF = if (oldLaF === manager.currentUIThemeLookAndFeel) {
          getDefaultLaf(isDark = isDark)
        }
        else {
          null
        }
        manager.updateLafComboboxModel()
        if (defaultLaF != null) {
          scheduleLafChange(defaultLaF)
        }
      }
    }
  }

  internal fun scheduleLafChange(defaultLaF: UIThemeLookAndFeelInfo?) {
    scheduledLaF = defaultLaF
    coroutineScope.launch(Dispatchers.EDT) {
      applyScheduledLaF()
    }
  }

  private fun applyScheduledLaF() {
    val laf = scheduledLaF ?: return
    scheduledLaF = null
    (serviceIfCreated<LafManager>() as? LafManagerImpl)?.applyScheduledLaF(laf)
  }

  fun pluginLoaded() {
    reloadColorSchemesAndApplyScheduledLaF()
    isUpdatingPlugin = false
    themeIdBeforePluginUpdate = null
  }

  fun beforePluginUnload(isUpdate: Boolean) {
    isUpdatingPlugin = isUpdate
    themeIdBeforePluginUpdate = LafManager.getInstance().currentUIThemeLookAndFeel?.id
  }
}