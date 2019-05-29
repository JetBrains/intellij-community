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

  override var iconPath: String? = null

  fun initGroup(target: ActionGroup) {
    ActionStub.copyTemplatePresentation(templatePresentation, target.templatePresentation)
    val children = getChildren(null)
    if (children.isNotEmpty()) {
      val defaultActionGroup = target as? DefaultActionGroup
                               ?: throw PluginException("Action group class must extend DefaultActionGroup for the group to accept children: $actionClass", pluginId)
      defaultActionGroup.addAll(children.toList())
    }
    target.isPopup = isPopup
  }
}
