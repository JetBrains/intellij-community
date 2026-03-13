// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.json

import org.jetbrains.intellij.build.productLayout.tooling.CommunityProductViolation
import org.jetbrains.intellij.build.productLayout.tooling.ModuleSetLocationViolation
import tools.jackson.core.JsonGenerator

/**
 * Writes community product validation violations to JSON.
 */
internal fun writeCommunityProductViolations(
  gen: JsonGenerator,
  violations: List<CommunityProductViolation>
) {
  gen.writeName("violations")
  gen.writeRawValue(kotlinxJson.encodeToString(violations))

  // Summary
  gen.writeObjectPropertyStart("summary")
  gen.writeNumberProperty("totalViolations", violations.size)
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
  gen.writeName("violations")
  gen.writeRawValue(kotlinxJson.encodeToString(violations))

  // Summary
  gen.writeObjectPropertyStart("summary")
  gen.writeNumberProperty("totalViolations", violations.size)
  gen.writeNumberProperty("communityContainsUltimate", violations.count { it.issue == "community_contains_ultimate" })
  gen.writeNumberProperty("ultimateContainsOnlyCommunity", violations.count { it.issue == "ultimate_contains_only_community" })
  gen.writeEndObject()
}
