// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

internal class ModulesWithDependencies(
  @JvmField val modules: List<PluginModuleDescriptor>,
  @JvmField val directDependencies: Map<PluginModuleDescriptor, List<PluginModuleDescriptor>>,
)