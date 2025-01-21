// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.intellij.util.asSafely
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector
import com.jetbrains.jsonSchema.impl.JsonSchemaObject

// todo: avoid casting after JsonSchemaObject is made an interface
internal fun inheritBaseSchemaIfNeeded(parent: JsonSchemaObject, child: JsonSchemaObject): JsonSchemaObject {
  val inheritedSchema = child.asSafely<JsonSchemaObjectBackedByJacksonBase>()
    ?.rootSchemaObject
    ?.schemaInterpretationStrategy
    ?.inheritBaseSchema(parent, child)
  if (inheritedSchema != null) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.SchemaInherited)
  }
  return inheritedSchema ?: child
}