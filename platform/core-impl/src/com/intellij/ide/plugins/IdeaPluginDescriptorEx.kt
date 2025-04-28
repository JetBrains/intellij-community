// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.plugins.parser.impl.elements.ActionElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface IdeaPluginDescriptorEx : IdeaPluginDescriptorImplPublic {
  val moduleLoadingRule: ModuleLoadingRule?

  /**
   * Ids of plugins which this plugin declares to be incompatible with.
   */
  val incompatiblePlugins: List<PluginId>

  val pluginAliases: List<PluginId>

  val packagePrefix: String?

  val contentModules: List<ContentModule>

  val actions: List<ActionElement>

  val appContainerDescriptor: ContainerDescriptor
  val projectContainerDescriptor: ContainerDescriptor
  val moduleContainerDescriptor: ContainerDescriptor

  /**
   * Qualified extension point name -> list of extension descriptors.
   *
   * This map contains extensions whose scope may be determined by extension points from other plugins, hence it is not part of a scoped elements container.
   */
  val extensions: Map<String, List<ExtensionDescriptor>>

  val useCoreClassLoader: Boolean
  val isUseIdeaClassLoader: Boolean

  /**
   * If false, the classloader of this descriptor uses core (platform) classloader as a fallback, otherwise, the system classloader is used instead.
   * TODO seems to be unused currently: there are no production plugin descriptor files with this attribute specified
   */
  val isIndependentFromCoreClassLoader: Boolean

  var isMarkedForLoading: Boolean
}

internal val IdeaPluginDescriptorEx.isRequiredContentModule: Boolean
  get() = moduleLoadingRule?.required == true