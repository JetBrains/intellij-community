// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import org.jetbrains.intellij.build.productLayout.CompositionType
import org.jetbrains.intellij.build.productLayout.analysis.ProductSpec

/**
 * Writes a single product to JSON.
 * Uses kotlinx.serialization to serialize ProductModulesContentSpec directly,
 * providing complete DSL structure with all modules, overrides, and exclusions.
 */
fun writeProduct(
  gen: JsonGenerator,
  product: ProductSpec
) {
  gen.writeStartObject()
  gen.writeStringField("name", product.name)
  gen.writeStringField("className", product.className)
  gen.writeStringField("sourceFile", product.sourceFile)
  
  if (product.pluginXmlPath != null) {
    gen.writeStringField("pluginXmlPath", product.pluginXmlPath)
  }
  
  // Serialize ProductModulesContentSpec using kotlinx.serialization and write raw JSON
  if (product.contentSpec != null) {
    val contentSpecJson = kotlinxJson.encodeToString(product.contentSpec)
    gen.writeFieldName("contentSpec")
    gen.writeRawValue(contentSpecJson)
  }
  
  // Build modules
  gen.writeArrayFieldStart("buildModules")
  for (buildModule in product.buildModules) {
    gen.writeString(buildModule)
  }
  gen.writeEndArray()

  // Metrics
  gen.writeNumberField("totalModuleCount", product.totalModuleCount)
  gen.writeNumberField("directModuleCount", product.directModuleCount)
  gen.writeNumberField("moduleSetCount", product.moduleSetCount)
  gen.writeNumberField("uniqueModuleCount", product.uniqueModuleCount)

  gen.writeEndObject()
}

/**
 * Analyzes product composition graphs and writes insights to JSON.
 * Provides statistics and recommendations for each product based on its composition.
 */
fun writeProductCompositionAnalysis(
  gen: JsonGenerator,
  products: List<ProductSpec>
) {
  gen.writeArrayFieldStart("products")

  for (product in products) {
    val contentSpec = product.contentSpec ?: continue
    val compositionGraph = contentSpec.compositionGraph

    if (compositionGraph.isEmpty()) continue

    gen.writeStartObject()
    gen.writeStringField("productName", product.name)

    // Count composition types
    val typeCounts = compositionGraph.groupBy { it.type }.mapValues { it.value.size }
    gen.writeObjectFieldStart("compositionCounts")
    for ((type, count) in typeCounts.entries.sortedBy { it.key.name }) {
      gen.writeNumberField(type.name.lowercase(), count)
    }
    gen.writeEndObject()

    // Total operations
    gen.writeNumberField("totalCompositionOperations", compositionGraph.size)

    // List module set references
    val moduleSetRefs = compositionGraph.filter { it.type == CompositionType.MODULE_SET_REF }
    if (moduleSetRefs.isNotEmpty()) {
      gen.writeArrayFieldStart("moduleSetReferences")
      for (ref in moduleSetRefs) {
        gen.writeStartObject()
        gen.writeStringField("name", ref.reference ?: "unknown")
        gen.writeArrayFieldStart("path")
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
      gen.writeArrayFieldStart("inlineSpecIncludes")
      for (spec in inlineSpecs) {
        gen.writeStartObject()
        gen.writeStringField("reference", spec.reference ?: "unknown")
        if (spec.sourceLocation != null) {
          gen.writeStringField("sourceFile", spec.sourceLocation)
        }
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }

    gen.writeEndObject()
  }

  gen.writeEndArray()
}
