// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import org.jetbrains.intellij.build.productLayout.CompositionType
import org.jetbrains.intellij.build.productLayout.tooling.ProductSpec
import tools.jackson.core.JsonGenerator

/**
 * Writes a single product to JSON using kotlinx.serialization.
 */
internal fun writeProduct(gen: JsonGenerator, product: ProductSpec) {
  gen.writeRawValue(kotlinxJson.encodeToString(product))
}

/**
 * Writes a name-indexed map of products for O(1) lookup on the client side.
 */
internal fun writeProductIndex(gen: JsonGenerator, products: List<ProductSpec>) {
  val entries = LinkedHashMap<String, ProductSpec>()
  for (product in products.sortedBy { it.name }) {
    entries[product.name] = product
  }
  gen.writeRawValue(kotlinxJson.encodeToString(entries))
}

/**
 * Analyzes product composition graphs and writes insights to JSON.
 * Provides statistics and recommendations for each product based on its composition.
 */
internal fun writeProductCompositionAnalysis(
  gen: JsonGenerator,
  products: List<ProductSpec>
) {
  gen.writeArrayPropertyStart("products")

  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    val compositionGraph = contentSpec.compositionGraph

    if (compositionGraph.isEmpty()) continue

    gen.writeStartObject()
    gen.writeStringProperty("productName", product.name)

    // Count composition types
    val typeCounts = compositionGraph.groupBy { it.type }.mapValues { it.value.size }
    gen.writeObjectPropertyStart("compositionCounts")
    for ((type, count) in typeCounts.entries.sortedBy { it.key.name }) {
      gen.writeNumberProperty(type.name.lowercase(), count)
    }
    gen.writeEndObject()

    // Total operations
    gen.writeNumberProperty("totalCompositionOperations", compositionGraph.size)

    // List module set references
    val moduleSetRefs = compositionGraph.filter { it.type == CompositionType.MODULE_SET_REF }
    if (moduleSetRefs.isNotEmpty()) {
      gen.writeArrayPropertyStart("moduleSetReferences")
      for (ref in moduleSetRefs) {
        gen.writeStartObject()
        gen.writeStringProperty("name", ref.reference ?: "unknown")
        gen.writeArrayPropertyStart("path")
        for (pathItem in ref.path) {
          gen.writeString(pathItem)
        }
        gen.writeEndArray()
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }

    // List inline spec includes
    val inlineSpecs = compositionGraph.filter { it.type == CompositionType.INLINE_SPEC }
    if (inlineSpecs.isNotEmpty()) {
      gen.writeArrayPropertyStart("inlineSpecIncludes")
      for (spec in inlineSpecs) {
        gen.writeStartObject()
        gen.writeStringProperty("reference", spec.reference ?: "unknown")
        if (spec.sourceLocation != null) {
          gen.writeStringProperty("sourceFile", spec.sourceLocation)
        }
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }

    gen.writeEndObject()
  }

  gen.writeEndArray()
}
