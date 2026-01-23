// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
class UIPluginGroup {
  @JvmField var panel: Component? = null
  @JvmField var plugins: MutableList<ListPluginComponent> = ArrayList()
  @JvmField var excluded: Boolean = false

  fun findComponent(pluginId: PluginId): ListPluginComponent? =
    plugins.find { pluginId == it.getPluginDescriptor().getPluginId() }
}