// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.KeyEvent

@Internal
object NoOpPluginsViewCustomizer : PluginsViewCustomizer {
  override fun getInternalPluginsGroupDescriptor(): PluginsViewCustomizer.PluginsGroupDescriptor? {
    return null
  }

  override fun getPluginDetailsCustomizer(pluginModel: MyPluginModel): PluginsViewCustomizer.PluginDetailsCustomizer {
    return NoOpPluginDetailsCustomizer
  }

  override fun getListPluginComponentCustomizer(): PluginsViewCustomizer.ListPluginComponentCustomizer {
    return NoOpListPluginComponentCustomizer
  }

  override fun processConfigurable(pluginManagerConfigurable: PluginManagerConfigurable) {}

  @Internal
  object NoOpListPluginComponentCustomizer : PluginsViewCustomizer.ListPluginComponentCustomizer {
    override fun processListPluginComponent(listPluginComponent: ListPluginComponent) {}
    override fun processCreateButtons(listPluginComponent: ListPluginComponent) {}
    override fun processRemoveButtons(listPluginComponent: ListPluginComponent) {}
    override fun processUpdateEnabledState(listPluginComponent: ListPluginComponent) {}
    override fun processHandleKeyAction(listPluginComponent: ListPluginComponent, event: KeyEvent, selection: List<ListPluginComponent>) {}
    override fun processCreatePopupMenu(
      listPluginComponent: ListPluginComponent,
      group: DefaultActionGroup,
      selection: List<ListPluginComponent>,
    ) {
    }
  }
}

private object NoOpPluginDetailsCustomizer : PluginsViewCustomizer.PluginDetailsCustomizer {
  override fun processPluginNameAndButtonsComponent(nameAndButtons: BaselinePanel) {}
  override fun processShowPlugin(pluginDescriptor: IdeaPluginDescriptor) {}
}