// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.keyFMap.KeyFMap
import com.jetbrains.jsonSchema.JsonPointerUtil
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.*
import com.jetbrains.jsonSchema.impl.light.*
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectLegacyAdapter
import com.jetbrains.jsonSchema.impl.light.legacy.isOldParserAwareOfFieldName
import com.jetbrains.jsonSchema.impl.light.legacy.tryReadEnumMetadata
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val ONE_OF_KEY = Key<List<JsonSchemaObject>>("oneOf")
private val ANY_OF_KEY = Key<List<JsonSchemaObject>>("anyOf")
private val ALL_OF_KEY = Key<List<JsonSchemaObject>>("allOf")
private val TYPE_VARIANTS_KEY = Key<Set<JsonSchemaType>>("typeVariants")
private val PATTERN_KEY = Key<PropertyNamePattern>("pattern")
private val PATTERN_PROPERTIES_KEY = Key<PatternProperties>("patternProperties")

private const val INVALID_PATTERN_FALLBACK = "__invalid_ij_pattern"

@ApiStatus.Internal
abstract class JsonSchemaObjectBackedByJacksonBase(
  override val rawSchemaNode: JsonNode,
  private val jsonPointer: String,
) : JsonSchemaObjectLegacyAdapter(), JsonSchemaNodePointer<JsonNode> {

  abstract override fun getRootSchemaObject(): RootJsonSchemaObjectBackedByJackson

  private fun getSchemaInterpretationStrategy(): JsonSchemaInterpretationStrategy {
    return getRootSchemaObject().schemaInterpretationStrategy
  }

  private var myCompositeObjectsCache = AtomicReference(KeyFMap.EMPTY_MAP)

  protected fun <V : Any> getOrComputeValue(key: Key<V>, idempotentComputation: () -> V): V {
    var existingMap: KeyFMap
    var newValue: V?

    do {
      existingMap = myCompositeObjectsCache.get()
      newValue = existingMap[key]
      if (newValue == null) {
        val mapWithNewValue = existingMap.plus(key, idempotentComputation())
        if (myCompositeObjectsCache.compareAndSet(existingMap, mapWithNewValue)) {
          newValue = mapWithNewValue.get(key)
        }
      }
    }
    while (newValue == null)

    return newValue
  }

  private fun createResolvableChild(vararg childNodeRelativePointer: String): JsonSchemaObjectBackedByJacksonBase? {
    // delegate to the root schema's factory - it is the only entry point for objects instantiation and caching
    return getRootSchemaObject().getChildSchemaObjectByName(this, *childNodeRelativePointer)
  }

  override fun getValidations(type: JsonSchemaType?, value: JsonValueAdapter): Iterable<JsonSchemaValidation> {
    return getSchemaInterpretationStrategy().getValidations(this, type, value).asIterable()
  }

  override fun getSchema(): String? {
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, SCHEMA_KEYWORD_INVARIANT)
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

  override fun hasChildFieldsExcept(namesToSkip: List<String>): Boolean {
    return JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode)
      .orEmpty()
      .any { it !in namesToSkip }
  }

  override fun hasChildNode(childNodeName: String): Boolean {
    return JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, childNodeName)
  }

  override fun readChildNodeValue(childNodeName: String): String? {
    return JacksonSchemaNodeAccessor.readUntypedNodeValueAsText(rawSchemaNode, childNodeName)
  }

  override fun getConstantSchema(): Boolean? {
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode)
  }

  override fun isValidByExclusion(): Boolean {
    return true
  }

  override fun getDeprecationMessage(): String? {
    val schemaFeature = getSchemaInterpretationStrategy().deprecationKeyword ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun getTypeVariants(): Set<JsonSchemaType?>? {
    val schemaFeature = getSchemaInterpretationStrategy().typeKeyword ?: return null
    return getOrComputeValue(TYPE_VARIANTS_KEY) {
      JacksonSchemaNodeAccessor.readUntypedNodesCollection(rawSchemaNode, schemaFeature)
        .orEmpty()
        .filterIsInstance<String>()
        .map(String::asUnquotedString)
        .mapNotNull(JsonSchemaReader::parseType)
        .toSet()
    }.takeIf { it.isNotEmpty() }
  }

  override fun getType(): JsonSchemaType? {
    val schemaFeature = getSchemaInterpretationStrategy().typeKeyword ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
      ?.let(JsonSchemaReader::parseType)
  }

  override fun getMultipleOf(): Number? {
    val schemaFeature = getSchemaInterpretationStrategy().multipleOfKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun getMaximum(): Number? {
    val schemaFeature = getSchemaInterpretationStrategy().maximumKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun isExclusiveMaximum(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().exclusiveMaximumKeyword ?: return false
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature) ?: false
  }

  override fun getExclusiveMaximumNumber(): Number? {
    val schemaFeature = getSchemaInterpretationStrategy().exclusiveMaximumKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun getExclusiveMinimumNumber(): Number? {
    val schemaFeature = getSchemaInterpretationStrategy().exclusiveMinimumKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun getMinimum(): Number? {
    val schemaFeature = getSchemaInterpretationStrategy().minimumKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun isExclusiveMinimum(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().exclusiveMaximumKeyword ?: return false
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature) ?: false
  }

  override fun getMaxLength(): Int? {
    val schemaFeature = getSchemaInterpretationStrategy().maxLengthKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature) as? Int
  }

  override fun getMinLength(): Int? {
    val schemaFeature = getSchemaInterpretationStrategy().minLengthKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature) as? Int
  }

  override fun getPattern(): String? {
    val schemaFeature = getSchemaInterpretationStrategy().patternKeyword ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
  }

  private fun getOrComputeCompiledPattern(): PropertyNamePattern {
    return getOrComputeValue(PATTERN_KEY) {
      val effectivePattern = pattern ?: INVALID_PATTERN_FALLBACK
      PropertyNamePattern(effectivePattern)
    }
  }

  override fun getPatternError(): String? {
    return getOrComputeCompiledPattern().patternError
  }

  override fun findRelativeDefinition(ref: String): JsonSchemaObject? {
    return resolveLocalSchemaNode(ref, this)
  }

  override fun getAdditionalPropertiesAllowed(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().additionalPropertiesKeyword ?: return true
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature) ?: true
  }

  override fun hasOwnExtraPropertyProhibition(): Boolean {
    return !additionalPropertiesAllowed
  }

  override fun getAdditionalPropertiesSchema(): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().additionalPropertiesKeyword ?: return null
    return createResolvableChild(schemaFeature)
      .takeIf { it?.rawSchemaNode?.isObject ?: false }
  }

  override fun getUnevaluatedPropertiesSchema(): JsonSchemaObject? {
    val additionalPropertiesKeyword = getSchemaInterpretationStrategy().additionalPropertiesKeyword ?: return null
    if (JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, additionalPropertiesKeyword)) return null

    val schemaFeature = getSchemaInterpretationStrategy().unevaluatedPropertiesKeyword ?: return null
    return createResolvableChild(schemaFeature)
  }

  override fun getPropertyNamesSchema(): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().propertyNamesKeyword ?: return null
    return createResolvableChild(schemaFeature)
  }

  override fun getAdditionalItemsAllowed(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().nonPositionalItemsKeyword ?: return true
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature) ?: true
  }

  override fun getAdditionalItemsSchema(): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().nonPositionalItemsKeyword ?: return null
    return createResolvableChild(schemaFeature)
  }

  override fun getItemsSchema(): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().itemsSchemaKeyword ?: return null
    return createResolvableChild(schemaFeature)
  }

  override fun getItemsSchemaList(): List<JsonSchemaObject?>? {
    val schemaFeature = getSchemaInterpretationStrategy().positionalItemsKeyword ?: return null
    return createIndexedItemsSequence(schemaFeature).takeIf { it.isNotEmpty() }
  }

  override fun getUnevaluatedItemsSchema(): JsonSchemaObject? {
    val nonPositionalItemsKeyword = getSchemaInterpretationStrategy().nonPositionalItemsKeyword ?: return null
    if (JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, nonPositionalItemsKeyword)) return null

    val schemaFeature = getSchemaInterpretationStrategy().unevaluatedItemsKeyword ?: return null
    return createResolvableChild(schemaFeature)
  }

  override fun getContainsSchema(): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().containsKeyword ?: return null
    return createResolvableChild(schemaFeature)
  }

  override fun getMaxItems(): Int? {
    val schemaFeature = getSchemaInterpretationStrategy().maxItemsKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature) as? Int
  }

  override fun getMinItems(): Int? {
    val schemaFeature = getSchemaInterpretationStrategy().minItemsKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature) as? Int
  }

  override fun isUniqueItems(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().uniqueItemsKeyword ?: return false
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature) ?: false
  }

  override fun getMaxProperties(): Int? {
    val schemaFeature = getSchemaInterpretationStrategy().maxPropertiesKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature) as? Int
  }

  override fun getMinProperties(): Int? {
    val schemaFeature = getSchemaInterpretationStrategy().minPropertiesKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature) as? Int
  }

  override fun getRequired(): Set<String>? {
    val schemaFeature = getSchemaInterpretationStrategy().requiredKeyword ?: return null
    return JacksonSchemaNodeAccessor.readUntypedNodesCollection(rawSchemaNode, schemaFeature)
      ?.filterIsInstance<String>()
      ?.map(String::asUnquotedString)
      ?.toSet()
  }

  override fun getRef(): String? {
    val ordinaryReferenceFeature = getSchemaInterpretationStrategy().referenceKeyword
    val dynamicReferenceFeature = getSchemaInterpretationStrategy().dynamicReferenceKeyword

    return sequenceOf(ordinaryReferenceFeature, dynamicReferenceFeature).filterNotNull().firstNotNullOfOrNull { referenceFeautre ->
      JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, referenceFeautre)
    }
  }

  override fun isRefRecursive(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().dynamicReferenceKeyword ?: return false
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature) ?: false
  }

  override fun isRecursiveAnchor(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().dynamicAnchorKeyword ?: return false
    return JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature) ?: false
  }

  override fun getDefault(): Any? {
    val schemaFeature = getSchemaInterpretationStrategy().defaultKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, schemaFeature)
           ?: JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, schemaFeature)
           ?: JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
           ?: createResolvableChild(schemaFeature)
  }

  override fun getExampleByName(name: String): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().exampleKeyword ?: return null
    return createResolvableChild(schemaFeature, name)
  }

  override fun getFormat(): String? {
    val schemaFeature = getSchemaInterpretationStrategy().formatKeyword ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun getId(): String? {
    val idFeature = getSchemaInterpretationStrategy().idKeyword
    val anchorFeature = getSchemaInterpretationStrategy().anchorKeyword

    val rawId = sequenceOf(idFeature, anchorFeature).filterNotNull().firstNotNullOfOrNull { schemaFeature ->
      JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
    } ?: return null
    return JsonPointerUtil.normalizeId(rawId)
  }

  override fun getDescription(): String? {
    val schemaFeature = getSchemaInterpretationStrategy().descriptionKeyword ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun getTitle(): String? {
    val schemaFeature = getSchemaInterpretationStrategy().titleKeyword ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, schemaFeature)
  }

  override fun getMatchingPatternPropertySchema(name: String): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().patternPropertiesKeyword ?: return null
    return getOrComputeValue(PATTERN_PROPERTIES_KEY) {
      PatternProperties(createChildMap(schemaFeature).orEmpty())
    }.getPatternPropertySchema(name)
  }

  override fun checkByPattern(value: String): Boolean {
    return getOrComputeCompiledPattern().checkByPattern(value)
  }

  override fun getPropertyDependencies(): Map<String, List<String?>?>? {
    val schemaFeature = getSchemaInterpretationStrategy().propertyDependenciesKeyword ?: return null
    return JacksonSchemaNodeAccessor.readNodeAsMultiMapEntries(rawSchemaNode, schemaFeature)?.toMap()
  }

  override fun getEnum(): List<Any?>? {
    val enumFeature = getSchemaInterpretationStrategy().enumKeyword
    val enum = if (enumFeature == null) null else JacksonSchemaNodeAccessor.readUntypedNodesCollection(rawSchemaNode, ENUM)?.toList()
    if (enum != null) return enum

    val constKeyword = getSchemaInterpretationStrategy().constKeyword ?: return null
    val number = JacksonSchemaNodeAccessor.readNumberNodeValue(rawSchemaNode, constKeyword)
    if (number != null) return listOf(number)
    val bool = JacksonSchemaNodeAccessor.readBooleanNodeValue(rawSchemaNode, constKeyword)
    if (bool != null) return listOf(bool)
    val text = JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, constKeyword)?.asDoubleQuotedString()
    if (text != null) return listOf(text)
    return null
  }

  override fun getAllOf(): List<JsonSchemaObject?>? {
    val schemaFeature = getSchemaInterpretationStrategy().allOfKeyword ?: return null
    return getOrComputeValue(ALL_OF_KEY) {
      createIndexedItemsSequence(schemaFeature)
    }.takeIf { it.isNotEmpty() }
  }

  override fun getAnyOf(): List<JsonSchemaObject?>? {
    val schemaFeature = getSchemaInterpretationStrategy().anyOfKeyword ?: return null
    return getOrComputeValue(ANY_OF_KEY) {
      createIndexedItemsSequence(schemaFeature)
    }.takeIf { it.isNotEmpty() }
  }

  override fun getOneOf(): List<JsonSchemaObject?>? {
    val schemaFeature = getSchemaInterpretationStrategy().oneOfKeyword ?: return null
    return getOrComputeValue(ONE_OF_KEY) {
      createIndexedItemsSequence(schemaFeature)
    }.takeIf { it.isNotEmpty() }
  }

  override fun getNot(): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().notKeyword ?: return null
    return createResolvableChild(schemaFeature)
  }

  override fun getIfThenElse(): List<IfThenElse?>? {
    val ifFeature = getSchemaInterpretationStrategy().ifKeyword ?: return null
    val thenFeature = getSchemaInterpretationStrategy().thenKeyword ?: return null
    val elseFeature = getSchemaInterpretationStrategy().elseKeyword ?: return null

    if (!JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, ifFeature)) return null
    return listOf(IfThenElse(createResolvableChild(ifFeature), createResolvableChild(thenFeature), createResolvableChild(elseFeature)))
  }

  override fun getDefinitionByName(name: String): JsonSchemaObject? {
    return getSchemaInterpretationStrategy().definitionsKeyword?.let { schemaFeature ->
      createResolvableChild(schemaFeature, name)
    }
  }

  override fun getDefinitionNames(): Iterator<String> {
    //todo really need it? ugly old hack
    fun defaultValue(): Iterator<String> = JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode)
                                             ?.filter { !isOldParserAwareOfFieldName(it) }
                                             ?.iterator()
                                           ?: Collections.emptyIterator()

    val schemaFeature = getSchemaInterpretationStrategy().definitionsKeyword ?: return defaultValue()
    return JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode, schemaFeature)?.iterator()
           ?: defaultValue()
  }

  override fun getPropertyByName(name: String): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().propertiesKeyword ?: return null
    return createResolvableChild(schemaFeature, name)
  }

  override fun getPropertyNames(): Iterator<String> {
    val schemaFeature = getSchemaInterpretationStrategy().propertiesKeyword ?: return Collections.emptyIterator()
    return JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode, schemaFeature)?.iterator() ?: Collections.emptyIterator()
  }

  override fun hasPatternProperties(): Boolean {
    val schemaFeature = getSchemaInterpretationStrategy().patternPropertiesKeyword ?: return false
    return JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, schemaFeature)
  }

  override fun getEnumMetadata(): Map<String, Map<String, String?>?>? {
    return tryReadEnumMetadata()
  }

  override fun toString(): String {
    // for debug purposes
    return renderSchemaNode(this, JsonSchemaObjectRenderingLanguage.JSON)
  }

  override fun getSchemaDependencyNames(): Iterator<String> {
    val schemaFeature = getSchemaInterpretationStrategy().dependencySchemasKeyword ?: return Collections.emptyIterator()
    return JacksonSchemaNodeAccessor.readNodeKeys(rawSchemaNode, schemaFeature)?.iterator() ?: Collections.emptyIterator()
  }

  override fun getSchemaDependencyByName(name: String): JsonSchemaObject? {
    val schemaFeature = getSchemaInterpretationStrategy().dependencySchemasKeyword ?: return null
    return createResolvableChild(schemaFeature, name)
  }

  private fun createIndexedItemsSequence(containingNodeName: String): List<JsonSchemaObject> {
    return generateSequence(0, Int::inc)
      .map { grandChildId -> createResolvableChild(containingNodeName, "$grandChildId") }
      .takeWhile { it != null }
      .filterNotNull()
      .toList()
  }

  private fun createChildMap(childMapName: String): Map<String, JsonSchemaObject>? {
    return JacksonSchemaNodeAccessor.readNodeAsMapEntries(rawSchemaNode, childMapName)
      ?.mapNotNull { (key, value) ->
        if (!value.isObject) return@mapNotNull null
        val childObject = createResolvableChild(childMapName, key) ?: return@mapNotNull null
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

  override fun getMetadata(): List<JsonSchemaMetadataEntry>? {
    return JacksonSchemaNodeAccessor.readNodeAsMapEntries(rawSchemaNode, X_INTELLIJ_METADATA)
      ?.mapNotNull {
        val values = (it.second as? ArrayNode)?.let {
          it.elements().asSequence().mapNotNull {
            it.takeIf { it.isTextual }?.asText()
          }.toList()
        } ?: it.second.takeIf { it.isTextual }?.asText()?.let { listOf(it) }
        if (values.isNullOrEmpty()) null
        else JsonSchemaMetadataEntry(it.first, values)
      }?.toList()
  }

  override fun getLanguageInjection(): String? {
    val directChild = JacksonSchemaNodeAccessor.readTextNodeValue(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION)
    if (directChild != null) return directChild

    val intermediateNode = JacksonSchemaNodeAccessor.resolveRelativeNode(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION) ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(intermediateNode, LANGUAGE)
  }

  override fun getLanguageInjectionPrefix(): String? {
    val intermediateNode = JacksonSchemaNodeAccessor.resolveRelativeNode(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION) ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(intermediateNode, PREFIX)
  }

  override fun getLanguageInjectionPostfix(): String? {
    val intermediateNode = JacksonSchemaNodeAccessor.resolveRelativeNode(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION) ?: return null
    return JacksonSchemaNodeAccessor.readTextNodeValue(intermediateNode, SUFFIX)
  }

  override fun isShouldValidateAgainstJSType(): Boolean {
    return JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode, INSTANCE_OF) || JacksonSchemaNodeAccessor.hasChildNode(rawSchemaNode,
                                                                                                                        TYPE_OF)
  }

  override fun resolveRefSchema(service: JsonSchemaService): JsonSchemaObject? {
    val effectiveReference = ref ?: return null
    return getSchemaInterpretationStrategy().referenceResolvers
      .firstNotNullOfOrNull { resolver -> resolver.resolve(effectiveReference, this, service) }
      ?.takeIf { it !is MissingJsonSchemaObject }
  }
}