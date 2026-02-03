// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import com.fasterxml.jackson.core.JsonGenerator
import org.jetbrains.intellij.build.productLayout.tooling.CommunityProductViolation
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetLocationViolation

/**
 * Writes community product validation violations to JSON.
 */
internal fun writeCommunityProductViolations(
  gen: JsonGenerator,
  violations: List<CommunityProductViolation>
) {
  gen.writeFieldName("violations")
  gen.writeRawValue(kotlinxJson.encodeToString(violations))

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
internal fun writeModuleSetLocationViolations(
  gen: JsonGenerator,
  violations: List<ModuleSetLocationViolation>
) {
  gen.writeFieldName("violations")
  gen.writeRawValue(kotlinxJson.encodeToString(violations))

  // Summary
  gen.writeObjectFieldStart("summary")
  gen.writeNumberField("totalViolations", violations.size)
  gen.writeNumberField("communityContainsUltimate", violations.count { it.issue == "community_contains_ultimate" })
  gen.writeNumberField("ultimateContainsOnlyCommunity", violations.count { it.issue == "ultimate_contains_only_community" })
  gen.writeEndObject()
}
