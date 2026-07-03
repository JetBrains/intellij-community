// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "ReplaceNegatedIsEmptyWithIsNotEmpty")

package com.intellij.ide.plugins

import com.intellij.util.graph.DFSTBuilder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PluginSetBuilder(
  private val initContext: PluginInitializationContext,
  @JvmField val unsortedPlugins: UnambiguousPluginSet,
  private val discoveryResult: PluginsDiscoveryResult,
) {
  private val moduleGraph: ModuleGraph
  private val sortedModulesWithDependencies: ModulesWithDependencies
  private val builder: DFSTBuilder<PluginModuleDescriptor>
  val topologicalComparator: Comparator<PluginModuleDescriptor>

  init {
    val (unsortedModulesWithDependencies, additionalEdges) = createModulesWithDependenciesAndAdditionalEdges(initContext, unsortedPlugins)
    moduleGraph = ModuleGraph(unsortedModulesWithDependencies, additionalEdges)
    builder = DFSTBuilder(moduleGraph, null, true)
    topologicalComparator = toCoreAwareComparator(builder.comparator())
    sortedModulesWithDependencies = unsortedModulesWithDependencies.sorted(topologicalComparator)
  }
}