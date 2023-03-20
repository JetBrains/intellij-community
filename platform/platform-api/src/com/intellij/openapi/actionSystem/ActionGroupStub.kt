// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId

interface ActionStubBase {
  val id: String

  @Deprecated(message = "Use plugin", replaceWith = ReplaceWith("plugin.pluginId"))
  val pluginId: PluginId?
    get() = plugin.pluginId

  val plugin: PluginDescriptor
  val iconPath: String?
}

class ActionGroupStub(override val id: String, val actionClass: String, override val plugin: IdeaPluginDescriptor) : DefaultActionGroup(), ActionStubBase {
  val classLoader: ClassLoader
    get() = plugin.classLoader

  var popupDefinedInXml = false

  override var iconPath: String? = null

  fun initGroup(target: ActionGroup, actionManager: ActionManager) {
    ActionStub.copyTemplatePresentation(templatePresentation, target.templatePresentation)
    copyActionTextOverrides(target)

    target.shortcutSet = shortcutSet
    val children = getChildren(null, actionManager)
    if (children.isNotEmpty()) {
      target as? DefaultActionGroup
      ?: throw PluginException("Action group class must extend DefaultActionGroup for the group to accept children: $actionClass", plugin.pluginId)
      for (action in children) {
        target.addAction(action, Constraints.LAST, actionManager)
      }
    }
    if (popupDefinedInXml) {
      target.isPopup = isPopup
    }
    target.isSearchable = isSearchable
  }
}
