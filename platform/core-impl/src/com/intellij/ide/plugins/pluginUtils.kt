// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("PluginUtils")
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

fun Iterable<IdeaPluginDescriptor>.toPluginIdSet(): Set<PluginId> = mapTo(LinkedHashSet()) { it.pluginId }

internal fun Iterable<PluginId>.toPluginDescriptors(): List<IdeaPluginDescriptorImpl> {
  val pluginIdMap = PluginManagerCore.buildPluginIdMap()
  return mapNotNull { pluginIdMap[it] }
}

internal fun Iterable<PluginId>.joinedPluginIds(operation: String): String =
  joinToString(prefix = "Plugins to $operation: [", postfix = "]") { it.idString }
