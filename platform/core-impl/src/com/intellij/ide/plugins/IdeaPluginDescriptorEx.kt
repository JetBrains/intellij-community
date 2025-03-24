// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.plugins.parser.impl.elements.ActionElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface IdeaPluginDescriptorEx : IdeaPluginDescriptorImplPublic {
  val moduleLoadingRule: ModuleLoadingRule?
  val useCoreClassLoader: Boolean
  val isIndependentFromCoreClassLoader: Boolean
  val incompatiblePlugins: List<PluginId>

  val actions: List<ActionElement>

  /**
   * Extension point name -> list of extension descriptors.
   *
   * Note that it does not contain some scoped extensions (such as listeners, services, extension points)
   */
  val miscExtensions: Map<String, List<ExtensionDescriptor>>
}

internal val IdeaPluginDescriptorEx.isRequiredContentModule: Boolean
  get() = moduleLoadingRule?.required == true