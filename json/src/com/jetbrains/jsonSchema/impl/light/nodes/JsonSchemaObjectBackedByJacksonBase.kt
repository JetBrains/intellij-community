// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.JsonPointerUtil
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.*
import com.jetbrains.jsonSchema.impl.light.*
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectLegacyAdapter
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils
import com.jetbrains.jsonSchema.impl.light.legacy.isOldParserAwareOfFieldName
import com.jetbrains.jsonSchema.impl.light.legacy.tryReadEnumMetadata
import java.util.*

internal abstract class JsonSchemaObjectBackedByJacksonBase(
  override val rawSchemaNode: JsonNode,
  private val jsonPointer: String
) : JsonSchemaObjectLegacyAdapter(), JsonSchemaNodePointer<JsonNode> {

  abstract fun getRootSchemaObject(): RootJsonSchemaObjectBackedByJackson

  private fun createResolvableChild(vararg childNodeRelativePointer: String): JsonSchemaObjectBackedByJacksonBase? {
    // delegate to the root schema's factory - it is the only entry point for objects instantiation and caching
    return getRootSchemaObject().schemaObjectFactory.getChildSchemaObjectByName(this, *childNodeRelativePointer)
  }

  override fun getPointer(): String {
    return jsonPointer
  }

  override fun getFileUrl(): String? {
    return getRootSchemaObject().fileUrl
  }

  override fun getRawFile(): VirtualFile? {
    return getRootSchemaObject().rawFile
  }

  override fun hasChildNode(vararg childNodeName: String): Boolean {
    return JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, *childNodeName)
  }

  override fun readChildNodeValue(vararg childNodeName: String): String? {
    return JacksonSchemaNodeAccessor.readUntypedNodeValueAsText(rawSchemaNode, *childNodeName)
  }

  override fun isValidByExclusion(): Boolean {
    return true
  }

  override fun resolveId(id: String): String? {
    return getRootSchemaObject().resolveId(id)
  }

  override fun getDeprecationMessage(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, DEPRECATION)
  }

  private val myVariants by lazy {
    JacksonSchemaNodeAccessor.readUntypedNodesCollection(rawSchemaNode, TYPE)
      ?.filterIsInstance<String>()
      ?.map(String::asUnquotedString)
      ?.map(JsonSchemaReader::parseType)
      ?.toSet()
  }

  override fun getTypeVariants(): Set<JsonSchemaType?>? {
    return myVariants
  }

  private val myType by lazy {
    JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, TYPE)
      ?.let(JsonSchemaReader::parseType)
  }

  override fun getType(): JsonSchemaType? {
    return myType
  }

  override fun getMultipleOf(): Number? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MULTIPLE_OF)
  }

  override fun getMaximum(): Number? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MAXIMUM)
  }

  override fun isExclusiveMaximum(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, EXCLUSIVE_MAXIMUM) ?: false
  }

  override fun getExclusiveMaximumNumber(): Number? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, EXCLUSIVE_MAXIMUM)
  }

  override fun getExclusiveMinimumNumber(): Number? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, EXCLUSIVE_MINIMUM)
  }

  override fun getMinimum(): Number? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MINIMUM)
  }

  override fun isExclusiveMinimum(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, EXCLUSIVE_MINIMUM) ?: false
  }

  override fun getMaxLength(): Int? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MAX_LENGTH) as? Int
  }

  override fun getMinLength(): Int? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MIN_LENGTH) as? Int
  }

  override fun getPattern(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, PATTERN)
  }

  private val myPattern by lazy {
    pattern?.let(::PropertyNamePattern)
  }

  override fun getPatternError(): String? {
    return myPattern?.patternError
  }

  override fun findRelativeDefinition(ref: String): JsonSchemaObject? {
    return resolveLocalReferenceOrId(ref)
  }

  private fun resolveLocalReferenceOrId(maybeEmptyReference: String?): JsonSchemaObject? {
    if (maybeEmptyReference == null) return null

    if (maybeEmptyReference.startsWith("#")) {
      val maybeCorrectJsonPointer = maybeEmptyReference.trimStart('#')
      val resolvedReference = getRootSchemaObject().schemaObjectFactory.getSchemaObjectByAbsoluteJsonPointer(maybeCorrectJsonPointer)
      if (resolvedReference != null) return resolvedReference
    }

    val resolvedIdNodePointer = resolveId(maybeEmptyReference).takeIf { !it.isNullOrBlank() } ?: return null
    return getRootSchemaObject().schemaObjectFactory.getSchemaObjectByAbsoluteJsonPointer(resolvedIdNodePointer)
  }

  override fun getAdditionalPropertiesAllowed(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, ADDITIONAL_PROPERTIES) ?: true
  }

  override fun hasOwnExtraPropertyProhibition(): Boolean {
    return !additionalPropertiesAllowed
  }

  override fun getAdditionalPropertiesSchema(): JsonSchemaObject? {
    return createResolvableChild(ADDITIONAL_PROPERTIES)
      .takeIf { it?.rawSchemaNode?.isObject ?: false }
  }

  override fun getPropertyNamesSchema(): JsonSchemaObject? {
    return createResolvableChild(PROPERTY_NAMES)
  }

  override fun getAdditionalItemsAllowed(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, ADDITIONAL_ITEMS) ?: true
  }

  override fun getAdditionalItemsSchema(): JsonSchemaObject? {
    return createResolvableChild(ADDITIONAL_ITEMS)
  }

  override fun getItemsSchema(): JsonSchemaObject? {
    return createResolvableChild(ITEMS)
  }

  override fun getItemsSchemaList(): List<JsonSchemaObject?>? {
    return createIndexedItemsSequence(ITEMS)
  }

  override fun getContainsSchema(): JsonSchemaObject? {
    return createResolvableChild(CONTAINS)
  }

  override fun getMaxItems(): Int? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MAX_ITEMS) as? Int
  }

  override fun getMinItems(): Int? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MIN_ITEMS) as? Int
  }

  override fun isUniqueItems(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, UNIQUE_ITEMS) ?: false
  }

  override fun getMaxProperties(): Int? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MAX_PROPERTIES) as? Int
  }

  override fun getMinProperties(): Int? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, MIN_PROPERTIES) as? Int
  }

  override fun getRequired(): Set<String>? {
    return JacksonSchemaNodeAccessor.readUntypedNodesCollection(rawSchemaNode, REQUIRED)
      ?.filterIsInstance<String>()
      ?.map(String::asUnquotedString)
      ?.toSet()
  }

  override fun getRef(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, REF)
           ?: JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, RECURSIVE_REF)
  }

  override fun isRefRecursive(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, RECURSIVE_REF) ?: false
  }

  override fun isRecursiveAnchor(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, RECURSIVE_ANCHOR) ?: false
  }

  override fun getDefault(): Any? {
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, DEFAULT)
           ?: JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, DEFAULT)
           ?: JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, DEFAULT)
           ?: createResolvableChild(DEFAULT)
  }

  override fun getExampleByName(name: String): JsonSchemaObject? {
    return createResolvableChild(EXAMPLE, name)
  }

  override fun getFormat(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, FORMAT)
  }

  override fun getId(): String? {
    val rawId = JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, JSON_ID)
                ?: JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, JSON_DOLLAR_ID)
                ?: JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, ANCHOR) ?: return null
    return JsonPointerUtil.normalizeId(rawId)
  }

  override fun getSchema(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, SCHEMA)
  }

  override fun getDescription(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, DESCRIPTION)
  }

  override fun getTitle(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, TITLE)
  }

  private val myPatternProperties by lazy {
    PatternProperties(createChildMap(PATTERN_PROPERTIES).orEmpty())
  }

  override fun getMatchingPatternPropertySchema(name: String): JsonSchemaObject? {
    return myPatternProperties.getPatternPropertySchema(name)
  }

  override fun checkByPattern(value: String): Boolean {
    return myPattern != null && myPattern!!.checkByPattern(value)
  }

  override fun getPropertyDependencies(): Map<String, List<String?>?>? {
    return JacksonSchemaNodeAccessor.readNodeAsMultiMapEntries(rawSchemaNode, DEPENDENCIES)?.toMap()
  }

  override fun getEnum(): List<Any?>? {
    val enum = JacksonSchemaNodeAccessor.readUntypedNodesCollection(rawSchemaNode, ENUM)?.toList()
    if (enum != null) return enum
    val number = JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, CONST)
    if (number != null) return listOf(number)
    val bool = JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, CONST)
    if (bool != null) return listOf(bool)
    val text = JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, CONST)?.asDoubleQuotedString()
    if (text != null) return listOf(text)
    return null
  }

  private val myAllOf by lazy {
    createIndexedItemsSequence(ALL_OF)
  }

  override fun getAllOf(): List<JsonSchemaObject?>? {
    return myAllOf
  }

  private val myAnyOf by lazy {
    createIndexedItemsSequence(ANY_OF)
  }

  override fun getAnyOf(): List<JsonSchemaObject?>? {
    return myAnyOf
  }

  private val myOneOf by lazy {
    createIndexedItemsSequence(ONE_OF)
  }

  override fun getOneOf(): List<JsonSchemaObject?>? {
    return myOneOf
  }

  override fun getNot(): JsonSchemaObject? {
    return createResolvableChild(NOT)
  }

  override fun getIfThenElse(): List<IfThenElse?>? {
    if (!JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, IF)) return null
    return listOf(IfThenElse(createResolvableChild(IF), createResolvableChild(THEN), createResolvableChild(ELSE)))
  }

  override fun getDefinitionByName(name: String): JsonSchemaObject? {
    return createResolvableChild(DEFS, name)
           ?: createResolvableChild(JSON_DEFINITIONS, name)
           ?: createResolvableChild(name)
  }

  override fun getDefinitionNames(): Iterator<String> {
    return JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode, DEFS)?.iterator()
           ?: JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode, JSON_DEFINITIONS)?.iterator()
           ?: JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode) //todo really need it? ugly old hack
             ?.filter { !isOldParserAwareOfFieldName(it) }
             ?.iterator()
           ?: Collections.emptyIterator()
  }

  override fun getPropertyByName(name: String): JsonSchemaObject? {
    return createResolvableChild(JSON_PROPERTIES, name)
  }

  override fun getPropertyNames(): Iterator<String> {
    return JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode, JSON_PROPERTIES)?.iterator() ?: Collections.emptyIterator()
  }

  override fun hasPatternProperties(): Boolean {
    return JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, PATTERN_PROPERTIES)
  }

  override fun getEnumMetadata(): Map<String, Map<String, String?>?>? {
    return tryReadEnumMetadata()
  }

  override fun toString(): String {
    // for debug purposes
    return renderSchemaNode(this, JsonSchemaObjectRenderingLanguage.JSON)
  }

  override fun getSchemaDependencyNames(): Iterator<String> {
    return JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode, DEPENDENCIES)?.iterator() ?: Collections.emptyIterator()
  }

  override fun getSchemaDependencyByName(name: String): JsonSchemaObject? {
    return createResolvableChild(DEPENDENCIES, name)
  }

  private fun createIndexedItemsSequence(containingNodeName: String): List<JsonSchemaObject>? {
    return generateSequence(0, Int::inc)
      .map { grandChildId -> createResolvableChild(containingNodeName, "$grandChildId") }
      .takeWhile { it != null }
      .filterNotNull()
      .toList()
      .takeIf { it.isNotEmpty() }
  }

  private fun createChildMap(vararg childMapName: String): Map<String, JsonSchemaObject>? {
    return JacksonSchemaNodeAccessor.readNodeAsMapEntries(rawSchemaNode, *childMapName)
      ?.mapNotNull { (key, value) ->
        if (!value.isObject) return@mapNotNull null
        val childObject = createResolvableChild(*childMapName, key) ?: return@mapNotNull null
        key to childObject
      }?.toMap()
  }

  /// candidates for removal
  override fun getSchemaDependencies(): Map<String, JsonSchemaObject>? {
    return createChildMap(DEPENDENCIES)
  }

  override fun isForceCaseInsensitive(): Boolean {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, X_INTELLIJ_CASE_INSENSITIVE) ?: false
  }

  override fun getHtmlDescription(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, X_INTELLIJ_HTML_DESCRIPTION)
  }

  override fun getLanguageInjection(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION)
           ?: JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION, LANGUAGE)
  }

  override fun getLanguageInjectionPrefix(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION, PREFIX)
  }

  override fun getLanguageInjectionPostfix(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION, SUFFIX)
  }

  override fun isShouldValidateAgainstJSType(): Boolean {
    return JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, INSTANCE_OF) || JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode,
                                                                                                                        TYPE_OF)
  }

  override fun resolveRefSchema(service: JsonSchemaService): JsonSchemaObject? {
    // fallback to old implementation in case of remote references/builtin schema ids
    val referenceTarget = resolveLocalReferenceOrId(ref) ?: JsonSchemaObjectReadingUtils.resolveRefSchema(this, service)
    return referenceTarget.takeIf { it !is MissingJsonSchemaObject }
  }
}