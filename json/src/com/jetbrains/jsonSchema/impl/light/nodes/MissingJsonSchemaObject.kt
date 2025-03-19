// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.node.MissingNode
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.impl.IfThenElse
import com.jetbrains.jsonSchema.impl.JsonSchemaMetadataEntry
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
  
  override fun getPointer(): String {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getFileUrl(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRawFile(): VirtualFile? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun hasPatternProperties(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRootSchemaObject(): RootJsonSchemaObjectBackedByJackson {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getType(): JsonSchemaType? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getConstantSchema(): Boolean? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMultipleOf(): Number? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaximum(): Number? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isExclusiveMaximum(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getExclusiveMaximumNumber(): Number? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getExclusiveMinimumNumber(): Number? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinimum(): Number? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isExclusiveMinimum(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaxLength(): Int? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinLength(): Int? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPattern(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalPropertiesAllowed(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun hasOwnExtraPropertyProhibition(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPropertyNamesSchema(): JsonSchemaObject? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalPropertiesSchema(): JsonSchemaObject? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalItemsAllowed(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDeprecationMessage(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAdditionalItemsSchema(): JsonSchemaObject? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getItemsSchema(): JsonSchemaObject? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getContainsSchema(): JsonSchemaObject? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getItemsSchemaList(): MutableList<out JsonSchemaObject>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaxItems(): Int? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinItems(): Int? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isUniqueItems(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMaxProperties(): Int? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMinProperties(): Int? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRequired(): MutableSet<String>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPropertyDependencies(): Map<String, List<String>>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getEnum(): MutableList<Any>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAllOf(): MutableList<out JsonSchemaObject>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getAnyOf(): MutableList<out JsonSchemaObject>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getOneOf(): MutableList<out JsonSchemaObject>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getNot(): JsonSchemaObject? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getIfThenElse(): MutableList<IfThenElse>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getTypeVariants(): MutableSet<JsonSchemaType>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getRef(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isRefRecursive(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isRecursiveAnchor(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDefault(): Any? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getFormat(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getId(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getSchema(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDescription(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getTitle(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMatchingPatternPropertySchema(name: String): JsonSchemaObject? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun checkByPattern(value: String): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getPatternError(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getEnumMetadata(): Map<String, Map<String, String?>?>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getHtmlDescription(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isForceCaseInsensitive(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getLanguageInjection(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getLanguageInjectionPrefix(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getLanguageInjectionPostfix(): String? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun isShouldValidateAgainstJSType(): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getDefinitionsMap(): MutableMap<String, out JsonSchemaObject>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getProperties(): Map<String, JsonSchemaObjectBackedByJacksonBase> {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getMetadata(): MutableList<JsonSchemaMetadataEntry>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun hasChildFieldsExcept(namesToSkip: List<String>): Boolean {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }

  override fun getSchemaDependencies(): Map<String, JsonSchemaObject>? {
    throw UnsupportedOperationException(ERROR_MESSAGE)
  }
}