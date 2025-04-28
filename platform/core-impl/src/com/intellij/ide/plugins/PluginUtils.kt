// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
object PluginUtils {
  @JvmStatic
  fun Iterable<IdeaPluginDescriptor>.toPluginIdSet(): Set<PluginId> = mapTo(LinkedHashSet()) { it.pluginId }

  @JvmStatic
  fun Iterable<String>.parseAsPluginIdSet(): Set<PluginId> = asSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .mapTo(LinkedHashSet(), PluginId::getId)

  @JvmStatic
  fun Iterable<PluginId>.toPluginDescriptors(): List<IdeaPluginDescriptorImpl> {
    val pluginIdMap = PluginManagerCore.buildPluginIdMap()
    return mapNotNull { pluginIdMap[it] }
  }

  @JvmStatic
  fun Iterable<PluginId>.joinedPluginIds(operation: String): String =
    joinToString(prefix = "Plugins to $operation: [", postfix = "]") { it.idString }

  /** don't expose user home in error messages */
  @JvmStatic
  fun pluginPathToUserString(file: Path): String =
    file.toString().replace("${System.getProperty("user.home")}${File.separatorChar}", "~${File.separatorChar}")
}
