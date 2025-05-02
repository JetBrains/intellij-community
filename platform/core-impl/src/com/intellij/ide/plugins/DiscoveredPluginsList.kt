// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DiscoveredPluginsList(
  val plugins: List<IdeaPluginDescriptorImpl>,
  val source: PluginsSourceContext
)

@ApiStatus.Internal
sealed interface PluginsSourceContext {
  object Product : PluginsSourceContext
  object Bundled : PluginsSourceContext
  object Custom : PluginsSourceContext
  object SystemPropertyProvided : PluginsSourceContext
  object ClassPathProvided : PluginsSourceContext
}
