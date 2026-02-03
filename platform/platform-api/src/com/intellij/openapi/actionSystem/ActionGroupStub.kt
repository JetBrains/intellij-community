// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

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