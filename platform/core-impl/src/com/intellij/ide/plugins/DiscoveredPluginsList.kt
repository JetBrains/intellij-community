// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
/**
 * Provides additional context for plugin sources in regard to plugin loading in the IDE.
 */
sealed interface DiscoveredPluginsList {
  val plugins: List<IdeaPluginDescriptorImpl>
}

@ApiStatus.Internal
class ProductPluginsList(
  override val plugins: List<IdeaPluginDescriptorImpl>,
) : DiscoveredPluginsList

@ApiStatus.Internal
class BundledPluginsList(
  val location: Path,
  override val plugins: List<IdeaPluginDescriptorImpl>,
) : DiscoveredPluginsList

@ApiStatus.Internal
class CustomPluginsList(
  val location: Path,
  override val plugins: List<IdeaPluginDescriptorImpl>,
) : DiscoveredPluginsList

@ApiStatus.Internal
class SystemPropertyProvidedPluginsList(
  override val plugins: List<IdeaPluginDescriptorImpl>,
) : DiscoveredPluginsList
