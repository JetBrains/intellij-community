// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.extensions.PluginId

interface ActionStubBase {
  val id: String
  val pluginId: PluginId?
  val iconPath: String?
}

class ActionGroupStub(
  override val id: String,
  val actionClass: String,
  val classLoader: ClassLoader,
  override val pluginId: PluginId
) : DefaultActionGroup(), ActionStubBase {
  var popupDefinedInXml = false

  override var iconPath: String? = null

  fun initGroup(target: ActionGroup, actionManager: ActionManager) {
    ActionStub.copyTemplatePresentation(templatePresentation, target.templatePresentation)
    target.shortcutSet = shortcutSet
    val children = getChildren(null, actionManager)
    if (children.isNotEmpty()) {
      target as? DefaultActionGroup
      ?: throw PluginException(
        "Action group class must extend DefaultActionGroup for the group to accept children: $actionClass",
        pluginId)
      for (action in children) {
        target.addAction(action, Constraints.LAST, actionManager)
      }
    }
    if (popupDefinedInXml) {
      target.isPopup = isPopup
    }
  }
}
