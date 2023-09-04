// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.ArrayUtil
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import org.jetbrains.annotations.ApiStatus

/** Use this if you want to have slightly different schema objects for your specific file */
@ApiStatus.Experimental
interface JsonSchemaMapper {
  companion object {
    val EXTENSION_POINT_NAME = ExtensionPointName.create<JsonSchemaMapper>("com.intellij.json.jsonSchemaMapper")
    @JvmStatic
    fun mapAll(file: PsiFile, schemaObject: CachedValueProvider.Result<JsonSchemaObject>): CachedValueProvider.Result<JsonSchemaObject> =
      EXTENSION_POINT_NAME
        .extensionsIfPointIsRegistered
        .fold(schemaObject) { acc, mapper ->
          mapper.map(file, acc.value)
            ?.let { next -> CachedValueProvider.Result.create(next.schemaObject, acc.dependencyItems + next.dependencies) }
            ?: acc
        }
  }

  /** Allows the user to provide additional dependencies for caching */
  class SchemaAndAdditionalCachingDependencies(val schemaObject: JsonSchemaObject, val dependencies: Array<Any>)

  /** @return null if you do not want to change the schemaObject for this file */
  fun map(file: PsiFile, schemaObject: JsonSchemaObject): SchemaAndAdditionalCachingDependencies?
}

@ApiStatus.Experimental
fun schemaWithoutAdditionalCachingDependencies(schema: JsonSchemaObject) =
  JsonSchemaMapper.SchemaAndAdditionalCachingDependencies(schema, ArrayUtil.EMPTY_OBJECT_ARRAY)

private operator fun Array<Any>.plus(other: Array<Any>): Array<Any> = other.copyInto(copyOf(size + other.size), size) as Array<Any>