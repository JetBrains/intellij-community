// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import org.jetbrains.intellij.build.productLayout.discovery.generateAllProductXmlFiles
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContext
import org.jetbrains.intellij.build.productLayout.pipeline.DataSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.PipelineNode
import org.jetbrains.intellij.build.productLayout.pipeline.ProductsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots

/**
 * Generator for product plugin.xml files.
 *
 * Generates complete `plugin.xml` files for products using programmatic content.
 * Includes `xi:include` directives for module sets and `<content>` blocks for modules.
 *
 * **Input:** Products from [GenerationModel.products] and test products from [GenerationModel.testProductSpecs]
 * **Output:** Updated product plugin.xml files
 *
 * **Publishes:** [Slots.PRODUCTS] with generation results
 *
 * **No dependencies** - can run immediately (level 0).
 *
 * @see org.jetbrains.intellij.build.productLayout.discovery.generateAllProductXmlFiles
 */
internal object ProductXmlGenerator : PipelineNode {
  override val id get() = NodeIds.PRODUCT_XML
  override val produces: Set<DataSlot<*>> get() = setOf(Slots.PRODUCTS)

  override suspend fun execute(ctx: ComputeContext) {
    val model = ctx.model
    val result = generateAllProductXmlFiles(
      discoveredProducts = model.discovery.products,
      testProductSpecs = model.discovery.testProductSpecs,
      projectRoot = model.projectRoot,
      outputProvider = model.outputProvider,
      strategy = model.fileUpdater,
    )

    ctx.publish(Slots.PRODUCTS, ProductsOutput(files = result.products))
  }
}
