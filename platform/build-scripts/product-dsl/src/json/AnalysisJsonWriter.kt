// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import org.jetbrains.intellij.build.productLayout.analysis.MergeImpactResult
import org.jetbrains.intellij.build.productLayout.analysis.ModulePathsResult
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetOverlap
import org.jetbrains.intellij.build.productLayout.analysis.ProductSimilarityPair
import org.jetbrains.intellij.build.productLayout.analysis.UnificationSuggestion

/**
 * Writes product similarity analysis to JSON.
 * Includes similar product pairs and summary statistics.
 */
fun writeProductSimilarityAnalysis(
  gen: JsonGenerator,
  pairs: List<ProductSimilarityPair>,
  threshold: Double
) {
  gen.writeArrayFieldStart("pairs")
  for (pair in pairs) {
    gen.writeStartObject()
    gen.writeStringField("product1", pair.product1)
    gen.writeStringField("product2", pair.product2)
    gen.writeNumberField("similarity", pair.similarity)
    gen.writeNumberField("moduleSetSimilarity", pair.moduleSetSimilarity)
    
    gen.writeArrayFieldStart("sharedModuleSets")
    for (setName in pair.sharedModuleSets) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("uniqueToProduct1")
    for (setName in pair.uniqueToProduct1) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeArrayFieldStart("uniqueToProduct2")
    for (setName in pair.uniqueToProduct2) {
      gen.writeString(setName)
    }
    gen.writeEndArray()
    
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeNumberField("totalPairs", pairs.size)
  gen.writeNumberField("threshold", threshold)
  gen.writeStringField("summary", "Found ${pairs.size} product pairs with ≥${(threshold * 100).toInt()}% similarity")
}

/**
 * Writes module set overlap analysis to JSON.
 * Includes overlapping module set pairs and summary statistics.
 * Note: Intentional nested set inclusions are already filtered out during analysis.
 */
fun writeModuleSetOverlapAnalysis(
  gen: JsonGenerator,
  overlaps: List<ModuleSetOverlap>,
  minPercent: Int
) {
  gen.writeArrayFieldStart("overlaps")
  for (overlap in overlaps) {
    gen.writeStartObject()
    gen.writeStringField("moduleSet1", overlap.moduleSet1)
    gen.writeStringField("moduleSet2", overlap.moduleSet2)
    gen.writeStringField("location1", overlap.location1)
    gen.writeStringField("location2", overlap.location2)
    gen.writeStringField("relationship", overlap.relationship)
    gen.writeNumberField("overlapPercent", overlap.overlapPercent)
    gen.writeNumberField("sharedModules", overlap.sharedModules)
    gen.writeNumberField("totalModules1", overlap.totalModules1)
    gen.writeNumberField("totalModules2", overlap.totalModules2)
    gen.writeStringField("recommendation", overlap.recommendation)
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeNumberField("count", overlaps.size)
  gen.writeStringField("summary", "Found ${overlaps.size} module set pairs with ≥$minPercent% overlap (excluding intentional nesting)")
}

/**
 * Writes module set unification suggestions to JSON.
 * Includes suggestions for merge, inline, factor, and split strategies.
 */
fun writeUnificationSuggestions(
  gen: JsonGenerator,
  suggestions: List<UnificationSuggestion>
) {
  gen.writeArrayFieldStart("suggestions")
  for (suggestion in suggestions) {
    gen.writeStartObject()
    gen.writeStringField("priority", suggestion.priority)
    gen.writeStringField("strategy", suggestion.strategy)
    
    if (suggestion.type != null) {
      gen.writeStringField("type", suggestion.type)
    }
    if (suggestion.moduleSet != null) {
      gen.writeStringField("moduleSet", suggestion.moduleSet)
    }
    if (suggestion.moduleSet1 != null) {
      gen.writeStringField("moduleSet1", suggestion.moduleSet1)
    }
    if (suggestion.moduleSet2 != null) {
      gen.writeStringField("moduleSet2", suggestion.moduleSet2)
    }
    if (suggestion.products != null) {
      gen.writeArrayFieldStart("products")
      for (product in suggestion.products) {
        gen.writeString(product)
      }
      gen.writeEndArray()
    }
    if (suggestion.sharedModuleSets != null) {
      gen.writeArrayFieldStart("sharedModuleSets")
      for (setName in suggestion.sharedModuleSets) {
        gen.writeString(setName)
      }
      gen.writeEndArray()
    }
    
    gen.writeStringField("reason", suggestion.reason)
    
    gen.writeObjectFieldStart("impact")
    for ((key, value) in suggestion.impact) {
      when (value) {
        is Number -> gen.writeNumberField(key, value.toDouble())
        is String -> gen.writeStringField(key, value)
        is List<*> -> {
          gen.writeArrayFieldStart(key)
          for (item in value) {
            gen.writeString(item.toString())
          }
          gen.writeEndArray()
        }
      }
    }
    gen.writeEndObject()
    
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeNumberField("totalSuggestions", suggestions.size)
  gen.writeStringField("summary", "Found ${suggestions.size} unification opportunities")
}

/**
 * Writes merge impact analysis to JSON.
 * Includes products affected, size impact, violations, and recommendation.
 */
fun writeMergeImpactAnalysis(
  gen: JsonGenerator,
  impact: MergeImpactResult
) {
  gen.writeStringField("operation", impact.operation)
  gen.writeStringField("sourceSet", impact.sourceSet)
  if (impact.targetSet != null) {
    gen.writeStringField("targetSet", impact.targetSet)
  }
  
  gen.writeArrayFieldStart("productsUsingSource")
  for (product in impact.productsUsingSource) {
    gen.writeString(product)
  }
  gen.writeEndArray()
  
  gen.writeArrayFieldStart("productsUsingTarget")
  for (product in impact.productsUsingTarget) {
    gen.writeString(product)
  }
  gen.writeEndArray()
  
  gen.writeArrayFieldStart("productsThatWouldChange")
  for (product in impact.productsThatWouldChange) {
    gen.writeString(product)
  }
  gen.writeEndArray()
  
  gen.writeObjectFieldStart("sizeImpact")
  for ((key, value) in impact.sizeImpact) {
    gen.writeNumberField(key, value)
  }
  gen.writeEndObject()
  
  gen.writeArrayFieldStart("violations")
  for (violation in impact.violations) {
    gen.writeStartObject()
    for ((key, value) in violation) {
      when (value) {
        is String -> gen.writeStringField(key, value)
        is Number -> gen.writeNumberField(key, value.toDouble())
        is List<*> -> {
          gen.writeArrayFieldStart(key)
          for (item in value) {
            gen.writeString(item.toString())
          }
          gen.writeEndArray()
        }
      }
    }
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeStringField("recommendation", impact.recommendation)
  gen.writeBooleanField("safe", impact.safe)
}

/**
 * Writes module paths result to JSON.
 * Includes all paths from module to products and summary information.
 */
fun writeModulePathsResult(
  gen: JsonGenerator,
  result: ModulePathsResult
) {
  gen.writeStringField("module", result.module)
  
  gen.writeArrayFieldStart("paths")
  for (path in result.paths) {
    gen.writeStartObject()
    gen.writeStringField("type", path.type)
    gen.writeStringField("path", path.path)
    
    gen.writeArrayFieldStart("files")
    for (file in path.files) {
      gen.writeStartObject()
      gen.writeStringField("type", file.type)
      if (file.path != null) {
        gen.writeStringField("path", file.path)
      }
      gen.writeStringField("name", file.name)
      gen.writeStringField("note", file.note)
      gen.writeEndObject()
    }
    gen.writeEndArray()
    
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  gen.writeArrayFieldStart("moduleSets")
  for (moduleSet in result.moduleSets) {
    gen.writeString(moduleSet)
  }
  gen.writeEndArray()
  
  gen.writeArrayFieldStart("products")
  for (product in result.products) {
    gen.writeString(product)
  }
  gen.writeEndArray()
}
