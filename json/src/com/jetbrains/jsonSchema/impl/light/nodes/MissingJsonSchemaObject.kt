// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.node.MissingNode
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.impl.IfThenElse
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import com.jetbrains.jsonSchema.impl.light.SCHEMA_ROOT_POINTER

private val ERROR_MESSAGE by lazy {
  "MissingJsonSchemaObject does not provide any meaningful method implementations"
}

internal object MissingJsonSchemaObject : JsonSchemaObjectBackedByJacksonBase(MissingNode.getInstance(), SCHEMA_ROOT_POINTER) {
  override fun isValidByExclusion(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun resolveId(id: String): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPointer(): String {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getFileUrl(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRawFile(): VirtualFile? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun hasPatternProperties(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRootSchemaObject(): RootJsonSchemaObjectBackedByJackson {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getType(): JsonSchemaType? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMultipleOf(): Number? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaximum(): Number? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isExclusiveMaximum(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getExclusiveMaximumNumber(): Number? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getExclusiveMinimumNumber(): Number? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinimum(): Number? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isExclusiveMinimum(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaxLength(): Int? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinLength(): Int? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPattern(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalPropertiesAllowed(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun hasOwnExtraPropertyProhibition(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPropertyNamesSchema(): JsonSchemaObject? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalPropertiesSchema(): JsonSchemaObject? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalItemsAllowed(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDeprecationMessage(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalItemsSchema(): JsonSchemaObject? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getItemsSchema(): JsonSchemaObject? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getContainsSchema(): JsonSchemaObject? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getItemsSchemaList(): MutableList<out JsonSchemaObject>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaxItems(): Int? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinItems(): Int? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isUniqueItems(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaxProperties(): Int? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinProperties(): Int? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRequired(): MutableSet<String>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPropertyDependencies(): Map<String, List<String>>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getEnum(): MutableList<Any>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAllOf(): MutableList<out JsonSchemaObject>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAnyOf(): MutableList<out JsonSchemaObject>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getOneOf(): MutableList<out JsonSchemaObject>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getNot(): JsonSchemaObject? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getIfThenElse(): MutableList<IfThenElse>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getTypeVariants(): MutableSet<JsonSchemaType>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRef(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isRefRecursive(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isRecursiveAnchor(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDefault(): Any? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getFormat(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getId(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getSchema(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDescription(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getTitle(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMatchingPatternPropertySchema(name: String): JsonSchemaObject? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun checkByPattern(value: String): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPatternError(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getEnumMetadata(): Map<String, Map<String, String?>?>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getHtmlDescription(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isForceCaseInsensitive(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getLanguageInjection(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getLanguageInjectionPrefix(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getLanguageInjectionPostfix(): String? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isShouldValidateAgainstJSType(): Boolean {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDefinitionsMap(): MutableMap<String, out JsonSchemaObject>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getProperties(): Map<String, JsonSchemaObjectBackedByJacksonBase> {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getSchemaDependencies(): Map<String, JsonSchemaObject>? {
    throw throw UnsupportedOperationException(ERROR_MESSAGE)
  }
}