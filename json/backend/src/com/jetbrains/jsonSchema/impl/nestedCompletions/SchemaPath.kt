// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

/** Represents a path through a tree structure. Often inversed using [accessor] */
data class SchemaPath(val name: String, val previous: SchemaPath?)

internal operator fun SchemaPath?.div(name: String): SchemaPath = SchemaPath(name, this)
internal fun SchemaPath.accessor(): List<String> = generateSequence(this) { it.previous }.toList().asReversed().map { it.name }
internal fun SchemaPath.prefix(): String = accessor().joinToString(".")