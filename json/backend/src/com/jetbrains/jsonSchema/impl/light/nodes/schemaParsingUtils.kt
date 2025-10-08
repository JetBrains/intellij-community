// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.asSafely
import com.intellij.util.io.URLUtil
import com.jetbrains.jsonSchema.impl.EnumArrayValueWrapper
import com.jetbrains.jsonSchema.impl.EnumObjectValueWrapper
import com.jetbrains.jsonSchema.impl.light.RawJsonSchemaNodeAccessor

internal object JacksonSchemaNodeAccessor : RawJsonSchemaNodeAccessor<JsonNode> {
  override fun resolveNode(rootNode: JsonNode, absoluteNodeJsonPointer: String): JsonNode? {
    val compiledPointer = escapeAndCompileJsonPointer(absoluteNodeJsonPointer) ?: return null
    return rootNode.at(compiledPointer)?.takeIf { it !is MissingNode }
  }

  override fun resolveRelativeNode(node: JsonNode, relativeChildPath: String?): JsonNode =
    getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)

  override fun hasChildNode(node: JsonNode, relativeChildPath: String): Boolean =
    node.isObject && !getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath).isMissingNode

  override fun readUntypedNodeValueAsText(node: JsonNode, relativeChildPath: String?): String? {
    if (!node.isObject && relativeChildPath != null) return null
    return getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)
      .takeIf { it !is MissingNode }
      ?.toPrettyString()
  }

  override fun readTextNodeValue(node: JsonNode, relativeChildPath: String?): String? {
    if (!node.isObject && relativeChildPath != null) return null
    val maybeString = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)
    return if (maybeString.isTextual) maybeString.asText() else null
  }

  override fun readBooleanNodeValue(node: JsonNode, relativeChildPath: String?): Boolean? {
    if (!(node.isObject || node.isBoolean && relativeChildPath == null)) return null
    val maybeBoolean = if (relativeChildPath == null) node else getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)
    return if (maybeBoolean.isBoolean) maybeBoolean.asBoolean() else null
  }

  override fun readNumberNodeValue(node: JsonNode, relativeChildPath: String?): Number? {
    if (!node.isObject && relativeChildPath != null) return null
    val maybeNumber = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)
    return when {
      maybeNumber.isInt -> maybeNumber.asInt()
      maybeNumber.isDouble -> maybeNumber.asDouble()
      maybeNumber.isLong -> maybeNumber.asLong()
      else -> null
    }
  }

  override fun readUntypedNodesCollection(node: JsonNode, relativeChildPath: String?): Sequence<Any>? =
    getChildArrayItems(node, relativeChildPath)
      ?.mapNotNull(JacksonSchemaNodeAccessor::readAnything)

  override fun readNodeAsMapEntries(node: JsonNode, relativeChildPath: String?): Sequence<Pair<String, JsonNode>>? {
    if (!node.isObject && relativeChildPath != null) return null
    return getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)
      .takeIf { it.isObject }
      ?.properties()
      ?.asSequence()
      ?.map { it.key to it.value }
  }

  override fun readNodeAsMultiMapEntries(node: JsonNode, relativeChildPath: String?): Sequence<Pair<String, List<String>>>? =
    readNodeAsMapEntries(node, relativeChildPath)
      ?.mapNotNull { (stringKey, arrayValue) ->
        if (!arrayValue.isArray) return@mapNotNull null
        stringKey to arrayValue.elements().asSequence().mapNotNull { if (it.isTextual) it.asText() else null }.toList()
      }

  override fun readNodeKeys(node: JsonNode, relativeChildPath: String?): Sequence<String>? {
    val expandedNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)
    return expandedNode.fieldNames().takeIf(Iterator<String>::hasNext)?.asSequence()
  }

  private fun getExistingChildByNonEmptyPathOrSelf(node: JsonNode, directChildName: String? = null): JsonNode =
    if (directChildName == null) node
    else node.get(directChildName) ?: MissingNode.getInstance()

  private fun getChildArrayItems(node: JsonNode, relativeChildPath: String?): Sequence<JsonNode>? =
    if (!node.isObject && relativeChildPath != null) null
    else getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath)
      .takeIf { it.isArray }
      ?.asSafely<ArrayNode>()
      ?.elements()
      ?.asSequence()

  private fun readAnything(node: JsonNode): Any? = when {
    node.isTextual -> asDoubleQuotedTextOrNull(node)
    node.isNull -> node.asText()
    node.isInt -> node.asInt()
    node.isLong -> node.asLong()
    node.isDouble -> node.asDouble()
    node.isBoolean -> node.asBoolean()
    node.isObject -> EnumObjectValueWrapper(node.properties().asSequence().mapNotNull { it.key to readAnything(it.value) }.toMap())
    node.isArray -> EnumArrayValueWrapper(node.elements().asSequence().mapNotNull(JacksonSchemaNodeAccessor::readAnything).toList().toTypedArray())
    else -> null
  }

  private fun asDoubleQuotedTextOrNull(jsonNode: JsonNode): String? =
    if (!jsonNode.isTextual) null
    else jsonNode.asText().asDoubleQuotedString()

  private fun escapeAndCompileJsonPointer(unescapedPointer: String): JsonPointer? =
    if (!fastCheckIfCorrectPointer(unescapedPointer)) null
    else try {
      JsonPointer.compile(adaptJsonPointerToJacksonImplementation(unescapedPointer))
    }
    catch (exception: IllegalArgumentException) {
      thisLogger().warn("Unable to compile json pointer. Resolve aborted.", exception)
      null
    }

  private fun fastCheckIfCorrectPointer(maybeIncorrectPointer: String): Boolean = maybeIncorrectPointer.startsWith("/")

  private fun adaptJsonPointerToJacksonImplementation(oldPointer: String): String =
    if (oldPointer == "/") ""
    else URLUtil.unescapePercentSequences(oldPointer)
}

internal fun String.asUnquotedString(): String = StringUtil.unquoteString(this)

internal fun String.asDoubleQuotedString(): String = this.asUnquotedString().let { "\"$it\"" }

internal fun escapeForbiddenJsonPointerSymbols(pointerSegment: String): String = pointerSegment.replace("~", "~0").replace("/", "~1")
