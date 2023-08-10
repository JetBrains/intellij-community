// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap

/**
 * Stores additional data related to plugin descriptors which can be used by [com.intellij.internal.DumpPluginDescriptorsAction].
 * Recording is disabled by default, may be enabled by setting a special system property (see [DescriptorListLoadingContext.debugData]).
 */
@Internal
class PluginDescriptorsDebugData {
  /** stores relative paths to files which content was included into the raw descriptor */
  private val includedInRawDescriptor = ConcurrentHashMap<RawPluginDescriptor, MutableList<String>>()
  /** stores relative paths to files which content was included into the descriptor */
  private val includedPaths = ConcurrentHashMap<String, MutableList<String>>()

  internal fun recordDescriptorPath(pluginDescriptor: IdeaPluginDescriptor, rawPluginDescriptor: RawPluginDescriptor, path: String) {
    val paths = includedPaths.computeIfAbsent(pluginDescriptor.uniqueId) { ArrayList() }
    paths.add(path)
    includedInRawDescriptor.remove(rawPluginDescriptor)?.let { paths.addAll(it) }
  }
  
  internal fun recordIncludedPath(rawPluginDescriptor: RawPluginDescriptor, path: String) {
    includedInRawDescriptor.computeIfAbsent(rawPluginDescriptor) { ArrayList() }.add(path)
  }

  fun getIncludedPaths(pluginDescriptor: IdeaPluginDescriptor): List<String> {
    return includedPaths[pluginDescriptor.uniqueId] ?: emptyList()
  }

  /**
   * Provides unique ID which can be used as a key for [IdeaPluginDescriptorImpl] ([IdeaPluginDescriptorImpl.equals] treat instances
   * corresponding to different modules are equal).
   */
  private val IdeaPluginDescriptor.uniqueId: String
    get() = "${pluginId.idString}:${(this as? IdeaPluginDescriptorImpl)?.moduleName ?: "main"}"
}