// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.IfThenElse
import com.jetbrains.jsonSchema.impl.JsonSchemaMetadataEntry
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object EmptyJsonSchemaObject: JsonSchemaObject() {
  override fun getConstantSchema(): Boolean? {
    return null
  }

  override fun hasChildFieldsExcept(namesToSkip: List<String>): Boolean {
    return false
  }

  override fun getValidations(
    type: JsonSchemaType?,
    value: JsonValueAdapter,
  ): Iterable<JsonSchemaValidation?> {
    return emptyList()
  }

  override fun getRootSchemaObject(): JsonSchemaObject {
    return this
  }

  override fun isValidByExclusion(): Boolean {
    return false
  }

  override fun getPointer(): String {
    return $$"$_NULL_$"
  }

  override fun getFileUrl(): String? {
    return null
  }

  override fun getRawFile(): VirtualFile? {
    return null
  }

  override fun hasPatternProperties(): Boolean {
    return false
  }

  override fun getType(): JsonSchemaType? {
    return null
  }

  override fun getMultipleOf(): Number? {
    return null
  }

  override fun getMaximum(): Number? {
    return null
  }

  override fun isExclusiveMaximum(): Boolean {
    return false
  }

  override fun getExclusiveMaximumNumber(): Number? {
    return null
  }

  override fun getExclusiveMinimumNumber(): Number? {
    return null
  }

  override fun getMinimum(): Number? {
    return null
  }

  override fun isExclusiveMinimum(): Boolean {
    return false
  }

  override fun getMaxLength(): Int? {
    return null
  }

  override fun getMinLength(): Int? {
    return null
  }

  override fun getPattern(): String? {
    return null
  }

  override fun getAdditionalPropertiesAllowed(): Boolean {
    return false
  }

  override fun hasOwnExtraPropertyProhibition(): Boolean {
    return false
  }

  override fun getPropertyNamesSchema(): JsonSchemaObject? {
    return null
  }

  override fun getAdditionalPropertiesSchema(): JsonSchemaObject? {
    return null
  }

  override fun getUnevaluatedPropertiesSchema(): JsonSchemaObject? {
    return null
  }

  override fun getAdditionalItemsAllowed(): Boolean? {
    return null
  }

  override fun getDeprecationMessage(): String? {
    return null
  }

  override fun getAdditionalItemsSchema(): JsonSchemaObject? {
    return null
  }

  override fun getItemsSchema(): JsonSchemaObject? {
    return null
  }

  override fun getUnevaluatedItemsSchema(): JsonSchemaObject? {
    return null
  }

  override fun getContainsSchema(): JsonSchemaObject? {
    return null
  }

  override fun getItemsSchemaList(): List<JsonSchemaObject?>? {
    return null
  }

  override fun getMaxItems(): Int? {
    return null
  }

  override fun getMinItems(): Int? {
    return null
  }

  override fun isUniqueItems(): Boolean {
    return false
  }

  override fun getMaxProperties(): Int? {
    return null
  }

  override fun getMinProperties(): Int? {
    return null
  }

  override fun getRequired(): Set<String?>? {
    return null
  }

  override fun getEnum(): List<Any?>? {
    return null
  }

  override fun getNot(): JsonSchemaObject? {
    return null
  }

  override fun getIfThenElse(): List<IfThenElse?>? {
    return null
  }

  override fun getTypeVariants(): Set<JsonSchemaType?>? {
    return null
  }

  override fun getRef(): String? {
    return null
  }

  override fun isRefRecursive(): Boolean {
    return false
  }

  override fun isRecursiveAnchor(): Boolean {
    return false
  }

  override fun getDefault(): Any? {
    return null
  }

  override fun getExampleByName(name: String): JsonSchemaObject? {
    return null
  }

  override fun getFormat(): String? {
    return null
  }

  override fun getId(): String? {
    return null
  }

  override fun getSchema(): String? {
    return null
  }

  override fun getDescription(): String? {
    return null
  }

  override fun getTitle(): String? {
    return null
  }

  override fun getMatchingPatternPropertySchema(name: String): JsonSchemaObject? {
    return null
  }

  override fun checkByPattern(value: String): Boolean {
    return false
  }

  override fun getPatternError(): String? {
    return null
  }

  override fun findRelativeDefinition(ref: String): JsonSchemaObject? {
    return null
  }

  override fun getEnumMetadata(): Map<String?, Map<String?, String?>?>? {
    return null
  }

  override fun getPropertyDependencies(): Map<String?, List<String?>?>? {
    return null
  }

  override fun getDefinitionByName(name: String): JsonSchemaObject? {
    return null
  }

  override fun getDefinitionNames(): Iterator<String?> {
    return emptyList<String?>().iterator()
  }

  override fun readChildNodeValue(childNodeName: String): String? {
    return null
  }

  override fun hasChildNode(childNodeName: String): Boolean {
    return false
  }

  override fun getPropertyNames(): Iterator<String?> {
    return emptyList<String?>().iterator()
  }

  override fun getPropertyByName(name: String): JsonSchemaObject? {
    return null
  }

  override fun getSchemaDependencyNames(): Iterator<String?> {
    return emptyList<String?>().iterator()
  }

  override fun getSchemaDependencyByName(name: String): JsonSchemaObject? {
    return null
  }

  override fun getMetadata(): List<JsonSchemaMetadataEntry?>? {
    return null
  }

  override fun getAllOf(): List<JsonSchemaObject?>? {
    return null
  }

  override fun getAnyOf(): List<JsonSchemaObject?>? {
    return null
  }

  override fun getOneOf(): List<JsonSchemaObject?>? {
    return null
  }

  override fun getSchemaDependencies(): Map<String?, JsonSchemaObject?>? {
    return null
  }

  override fun getHtmlDescription(): String? {
    return null
  }

  override fun isForceCaseInsensitive(): Boolean {
    return false
  }

  override fun getLanguageInjection(): String? {
    return null
  }

  override fun getLanguageInjectionPrefix(): String? {
    return null
  }

  override fun getLanguageInjectionPostfix(): String? {
    return null
  }

  override fun isShouldValidateAgainstJSType(): Boolean {
    return false
  }

  override fun resolveRefSchema(service: JsonSchemaService): JsonSchemaObject? {
    return null
  }
}