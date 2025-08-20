// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IdeaPluginDescriptorExtensions")
@file:ApiStatus.Experimental
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@Deprecated("Use `contentModuleId`", ReplaceWith("contentModuleId"))
@get:ApiStatus.Experimental
val IdeaPluginDescriptor.contentModuleName: String?
  get() = (this as? ContentModuleDescriptor)?.moduleId?.id

@get:ApiStatus.Experimental
val IdeaPluginDescriptor.contentModuleId: String?
  get() = (this as? ContentModuleDescriptor)?.moduleId?.id

@get:ApiStatus.Experimental
val IdeaPluginDescriptor.isRequiredContentModule: Boolean
  get() = (this as? ContentModuleDescriptor)?.moduleLoadingRule?.required == true

/**
 * A dependency from [pluginIds] in fact means a module dependency on the *implicit main module* of a given plugin.
 */
@ApiStatus.Experimental
class ModuleDependenciesApi(val pluginIds: List<String>, val moduleIds: List<String>)

/**
 * aka `<dependencies>` element from plugin.xml
 *
 * Note that it's different from [IdeaPluginDescriptor.getDependencies] (which is for `<depends>`)
 */
@get:ApiStatus.Experimental
val IdeaPluginDescriptor.moduleDependencies: ModuleDependenciesApi
  get() = (this as IdeaPluginDescriptorImpl).moduleDependencies.let {
    ModuleDependenciesApi(it.plugins.map { it.idString }, it.modules.map { it.id })
  }

@get:ApiStatus.Experimental
val IdeaPluginDescriptor.contentModules: List<IdeaPluginDescriptor>
  get() = if (this is PluginMainDescriptor) contentModules else emptyList()

@ApiStatus.Experimental
fun IdeaPluginDescriptor.getMainDescriptor(): IdeaPluginDescriptor = (this as IdeaPluginDescriptorImpl).getMainDescriptor()