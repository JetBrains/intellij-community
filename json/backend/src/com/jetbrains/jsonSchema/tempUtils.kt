// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema

import com.intellij.openapi.util.registry.Registry

fun isJsonSchemaObjectV2(): Boolean {
  return Registry.`is`("json.schema.object.v2")
}