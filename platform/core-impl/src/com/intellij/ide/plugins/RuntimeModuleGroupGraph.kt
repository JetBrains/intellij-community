// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RuntimeModuleGroupGraph {
  /** topologically sorted */
  val sortedGroups: List<RuntimeModuleGroup>

  fun getRuntimeModuleGroup(resolvedDescriptor: IdeaPluginDescriptorImpl): RuntimeModuleGroup

  fun getDirectDependencies(group: RuntimeModuleGroup): List<RuntimeModuleGroup>
  fun getDirectDependents(group: RuntimeModuleGroup): List<RuntimeModuleGroup>
}


/** Each group is formed from a subset of descriptors that can later be associated with the same classloader */
@ApiStatus.Internal
interface RuntimeModuleGroup {
  val representativeModule: PluginModuleDescriptor
  val sortedDescriptors: List<IdeaPluginDescriptorImpl>
}