// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

/**
 * This interface will eventually be removed, it is only needed for incremental migration.
 * A migration guide will be provided when alternatives will become available
 */
@ApiStatus.Experimental
interface IdeaPluginDescriptorImplPublic : IdeaPluginDescriptor

@get:ApiStatus.Experimental
val IdeaPluginDescriptorImplPublic.contentModuleName: String?
  get() = (this as? ContentModuleDescriptor)?.moduleName

@get:ApiStatus.Experimental
val IdeaPluginDescriptorImplPublic.isRequiredContentModule: Boolean
  get() = (this as? ContentModuleDescriptor)?.moduleLoadingRule?.required == true

/**
 * aka `<dependencies>` element from plugin.xml
 *
 * Note that it's different from [IdeaPluginDescriptor.getDependencies] (which is for `<depends>`)
 */
@get:ApiStatus.Experimental
val IdeaPluginDescriptorImplPublic.moduleDependencies: ModuleDependencies
  get() = (this as IdeaPluginDescriptorImpl).moduleDependencies