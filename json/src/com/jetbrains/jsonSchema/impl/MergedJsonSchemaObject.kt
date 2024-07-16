// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.jetbrains.jsonSchema.impl.light.nodes.InheritedJsonSchemaObjectView

interface MergedJsonSchemaObject {
  val base: JsonSchemaObject
  val other: JsonSchemaObject

  val isInherited: Boolean
    get() {
      val baseRef = base
      val otherRef = other
      return when {
        this is InheritedJsonSchemaObjectView || baseRef is InheritedJsonSchemaObjectView || otherRef is InheritedJsonSchemaObjectView -> true
        baseRef is MergedJsonSchemaObject && baseRef.isInherited -> true
        otherRef is MergedJsonSchemaObject && otherRef.isInherited -> true
        else -> false
      }
    }
}