// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginGraph
import com.intellij.platform.pluginGraph.ProductNode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.productLayout.model.error.ValidationError
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext

internal suspend inline fun PluginGraph.forEachProductParallel(crossinline action: suspend (ProductNode) -> Unit) {
  coroutineScope {
    query {
      products { product ->
        launch { action(product) }
      }
    }
  }
}

internal suspend inline fun ComputeContext.emitErrorsPerProduct(
  graph: PluginGraph,
  crossinline validator: (ProductNode) -> List<ValidationError>,
) {
  graph.forEachProductParallel { product ->
    emitErrors(validator(product))
  }
}
