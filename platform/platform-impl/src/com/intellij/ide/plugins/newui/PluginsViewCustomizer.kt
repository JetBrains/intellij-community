// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.KeyEvent


/**
 * Allows to customize certain aspects of Marketplace UI from plugins.
 *
 * Primary goals:
 * - contribute custom plugin group from a plugin
 * - add channel selector combobox to plugin details component
 * - modify behaviour of existing controls in plugin details component
 *
 * This extension is expected to be used from Toolbox Enterprise plugin, however certain features
 * like plugin downgrading and update channels can be considered to become a part of IJ platform in the future.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface PluginsViewCustomizer {

  interface PluginDetailsCustomizer {
    fun processPluginNameAndButtonsComponent(nameAndButtons: BaselinePanel)
    fun processShowPlugin(pluginDescriptor: IdeaPluginDescriptor)
  }

  interface ListPluginComponentCustomizer {
    fun processListPluginComponent(listPluginComponent: ListPluginComponent)
    fun processCreateButtons(listPluginComponent: ListPluginComponent)
    fun processRemoveButtons(listPluginComponent: ListPluginComponent)
    fun processUpdateEnabledState(listPluginComponent: ListPluginComponent)
    fun processCreatePopupMenu(listPluginComponent: ListPluginComponent, group: DefaultActionGroup, selection: List<ListPluginComponent>)
    fun processHandleKeyAction(listPluginComponent: ListPluginComponent, event: KeyEvent, selection: List<ListPluginComponent>)
  }

  data class PluginsGroupDescriptor(
    val name: @Nls String,
    val plugins: List<IdeaPluginDescriptor>,
    val showAllQuery: String,
  )

  fun getInternalPluginsGroupDescriptor(): PluginsGroupDescriptor?

  fun getPluginDetailsCustomizer(pluginModel: MyPluginModel): PluginDetailsCustomizer

  fun getListPluginComponentCustomizer(): ListPluginComponentCustomizer

  fun processConfigurable(pluginManagerConfigurable: PluginManagerConfigurable)
}

val pluginsViewCustomizerEP = ExtensionPointName.create<PluginsViewCustomizer>("com.intellij.pluginsViewCustomizer")

fun getPluginsViewCustomizer(): PluginsViewCustomizer =
  pluginsViewCustomizerEP.extensionsIfPointIsRegistered.getOrNull(0) ?: NoOpPluginsViewCustomizer

fun getListPluginComponentCustomizer(): PluginsViewCustomizer.ListPluginComponentCustomizer =
  getPluginsViewCustomizer().getListPluginComponentCustomizer()

