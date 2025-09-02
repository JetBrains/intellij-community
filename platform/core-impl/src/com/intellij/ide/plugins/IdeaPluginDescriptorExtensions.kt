// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IdeaPluginDescriptorExtensions")
@file:ApiStatus.Experimental
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.Experimental
val IdeaPluginDescriptor.contentModuleName: String?
  get() = (this as? ContentModuleDescriptor)?.moduleName

@get:ApiStatus.Experimental
val IdeaPluginDescriptor.isRequiredContentModule: Boolean
  get() = (this as? ContentModuleDescriptor)?.moduleLoadingRule?.required == true

/**
 * aka `<dependencies>` element from plugin.xml
 *
 * Note that it's different from [IdeaPluginDescriptor.getDependencies] (which is for `<depends>`)
 */
@get:ApiStatus.Experimental
val IdeaPluginDescriptor.moduleDependencies: ModuleDependencies
  get() = (this as IdeaPluginDescriptorImpl).moduleDependencies

@get:ApiStatus.Experimental
val IdeaPluginDescriptor.contentModules: List<IdeaPluginDescriptor>
  get() = if (this is PluginMainDescriptor) contentModules else emptyList()

@ApiStatus.Experimental
fun IdeaPluginDescriptor.getMainDescriptor(): IdeaPluginDescriptor = (this as IdeaPluginDescriptorImpl).getMainDescriptor()