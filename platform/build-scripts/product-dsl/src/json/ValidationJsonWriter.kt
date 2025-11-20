// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import org.jetbrains.intellij.build.productLayout.analysis.CommunityProductViolation
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetLocationViolation

/**
 * Writes community product validation violations to JSON.
 */
fun writeCommunityProductViolations(
  gen: JsonGenerator,
  violations: List<CommunityProductViolation>
) {
  gen.writeArrayFieldStart("violations")
  for (violation in violations) {
    gen.writeStartObject()
    gen.writeStringField("product", violation.product)
    gen.writeStringField("productFile", violation.productFile)
    gen.writeStringField("moduleSet", violation.moduleSet)
    gen.writeStringField("moduleSetFile", violation.moduleSetFile)
    
    gen.writeStringArray("ultimateModules", violation.ultimateModules)
    
    gen.writeNumberField("communityModulesCount", violation.communityModulesCount)
    gen.writeNumberField("unknownModulesCount", violation.unknownModulesCount)
    gen.writeNumberField("totalModulesCount", violation.totalModulesCount)
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  // Summary
  gen.writeObjectFieldStart("summary")
  gen.writeNumberField("totalViolations", violations.size)
  
  gen.writeStringArray("affectedProducts", violations.map { it.product }.distinct().sorted())
  gen.writeStringArray("affectedModuleSets", violations.map { it.moduleSet }.distinct().sorted())
  
  gen.writeEndObject()
}

/**
 * Writes module set location validation violations to JSON.
 */
fun writeModuleSetLocationViolations(
  gen: JsonGenerator,
  violations: List<ModuleSetLocationViolation>
) {
  gen.writeArrayFieldStart("violations")
  for (violation in violations) {
    gen.writeStartObject()
    gen.writeStringField("moduleSet", violation.moduleSet)
    gen.writeStringField("file", violation.file)
    gen.writeStringField("issue", violation.issue)
    
    if (violation.ultimateModules != null) {
      gen.writeStringArray("ultimateModules", violation.ultimateModules)
    }

    if (violation.communityModules != null) {
      gen.writeStringArray("communityModules", violation.communityModules)
    }
    
    if (violation.communityModulesCount != null) {
      gen.writeNumberField("communityModulesCount", violation.communityModulesCount)
    }
    
    if (violation.ultimateModulesCount != null) {
      gen.writeNumberField("ultimateModulesCount", violation.ultimateModulesCount)
    }
    
    gen.writeNumberField("unknownModulesCount", violation.unknownModulesCount)
    gen.writeStringField("suggestion", violation.suggestion)
    gen.writeEndObject()
  }
  gen.writeEndArray()
  
  // Summary
  gen.writeObjectFieldStart("summary")
  gen.writeNumberField("totalViolations", violations.size)
  gen.writeNumberField("communityContainsUltimate", violations.count { it.issue == "community_contains_ultimate" })
  gen.writeNumberField("ultimateContainsOnlyCommunity", violations.count { it.issue == "ultimate_contains_only_community" })
  gen.writeEndObject()
}
