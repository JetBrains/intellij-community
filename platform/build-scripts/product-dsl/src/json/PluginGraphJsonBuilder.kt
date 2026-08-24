// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.intellij.platform.pluginGraph.PluginGraph
import kotlinx.coroutines.coroutineScope
import org.jetbrains.intellij.build.productLayout.discovery.ModuleSetGenerationConfig
import org.jetbrains.intellij.build.productLayout.generator.ContentModuleDependencyPlanner
import org.jetbrains.intellij.build.productLayout.model.ErrorSink
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContextImpl
import org.jetbrains.intellij.build.productLayout.pipeline.DiscoveryStage
import org.jetbrains.intellij.build.productLayout.pipeline.ModelBuildingStage
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.Slots

suspend fun buildPluginGraphForJson(config: ModuleSetGenerationConfig): PluginGraph {
  return coroutineScope {
    val discovery = DiscoveryStage.execute(config)
    val errorSink = ErrorSink()
    val model = ModelBuildingStage.execute(
      discovery = discovery,
      config = config,
      scope = this,
      updateSuppressions = false,
      commitChanges = false,
      errorSink = errorSink,
    )
    val context = ComputeContextImpl(model)
    context.initSlot(Slots.CONTENT_MODULE_PLAN)
    ContentModuleDependencyPlanner.execute(context.forNode(NodeIds.CONTENT_MODULE_DEPS))
    model.pluginGraph
  }
}
