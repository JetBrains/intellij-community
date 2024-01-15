// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.intellij.openapi.diagnostic.Logger
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

  override fun resolveRelativeNode(node: JsonNode, vararg relativeChildPath: String): JsonNode? {
    return relativeChildPath.asSequence().map(::escapeForbiddenJsonPointerSymbols).fold(node) { currentNode, childName ->
      val childByName = currentNode.get(childName)
      if (childByName != null) return@fold childByName

      val maybeIndex = childName.toIntOrNull() ?: return@fold MissingNode.getInstance()
      return currentNode.get(maybeIndex)
    }.takeIf { it !is MissingNode }
  }

  override fun hasChildNode(node: JsonNode, vararg relativeChildPath: String): Boolean {
    if (!node.isObject) return false
    return !getChild(node, *relativeChildPath).isMissingNode
  }

  override fun readUntypedNodeValueAsText(node: JsonNode, vararg relativeChildPath: String): String? {
    if (!node.isObject) return null
    return getChild(node, *relativeChildPath)
      .takeIf { it !is MissingNode }
      ?.asText()
  }

  override fun readTextNodeValue(node: JsonNode, vararg relativeChildPath: String): String? {
    if (!node.isObject) return null
    val maybeString = getChild(node, *relativeChildPath)
    return if (maybeString.isTextual)
      maybeString.asText()
    else
      null
  }

  override fun readBooleanNodeValue(node: JsonNode, vararg relativeChildPath: String): Boolean? {
    if (!node.isObject) return null
    val maybeBoolean = getChild(node, *relativeChildPath)
    return if (maybeBoolean.isBoolean)
      maybeBoolean.asBoolean()
    else
      null
  }

  override fun readNumberNodeValue(node: JsonNode, vararg relativeChildPath: String): Number? {
    if (!node.isObject) return null
    val maybeNumber = getChild(node, *relativeChildPath)
    return when {
      maybeNumber.isInt -> maybeNumber.asInt()
      maybeNumber.isDouble -> maybeNumber.asDouble()
      maybeNumber.isLong -> maybeNumber.asLong()
      else -> null
    }
  }

  override fun readUntypedNodesCollection(node: JsonNode, vararg relativeChildPath: String): Sequence<Any>? {
    return getChildArrayItems(node, *relativeChildPath)
      ?.mapNotNull(JacksonSchemaNodeAccessor::readAnything)
  }

  override fun readNodeAsMapEntries(node: JsonNode, vararg relativeChildPath: String): Sequence<Pair<String, JsonNode>>? {
    if (!node.isObject) return null
    return getChild(node, *relativeChildPath)
      .takeIf { it.isObject }
      ?.fields()
      ?.asSequence()
      ?.map { it.key to it.value }
  }

  override fun readNodeAsMultiMapEntries(node: JsonNode, vararg relativeChildPath: String): Sequence<Pair<String, List<String>>>? {
    return readNodeAsMapEntries(node, *relativeChildPath)
      ?.mapNotNull { (stringKey, arrayValue) ->
        if (!arrayValue.isArray) return@mapNotNull null
        stringKey to arrayValue.elements().asSequence().mapNotNull { if (it.isTextual) it.asText() else null }.toList()
      }
  }

  override fun readNodeKeys(node: JsonNode, vararg relativeChildPath: String): Sequence<String>? {
    val expandedNode = if (relativeChildPath.isEmpty())
      node
    else
      getChild(node, *relativeChildPath)
    return expandedNode.fieldNames().takeIf(Iterator<String>::hasNext)?.asSequence()
  }

  private fun getChild(node: JsonNode, vararg relativePath: String): JsonNode {
    return relativePath.fold(node) { parentNode, childName ->
      parentNode.get(childName) ?: return MissingNode.getInstance()
    }
  }

  private fun getChildArrayItems(node: JsonNode, vararg name: String): Sequence<JsonNode>? {
    if (!node.isObject) return null
    return getChild(node, *name)
      .takeIf { it.isArray }
      ?.asSafely<ArrayNode>()
      ?.elements()
      ?.asSequence()
  }

  private fun readAnything(node: JsonNode): Any? {
    return when {
      node.isTextual -> asDoubleQuotedTextOrNull(node)
      node.isNull -> node.asText()
      node.isInt -> node.asInt()
      node.isLong -> node.asLong()
      node.isDouble -> node.asDouble()
      node.isBoolean -> node.asBoolean()
      node.isObject -> EnumObjectValueWrapper(node.fields().asSequence().mapNotNull { it.key to readAnything(it.value) }.toMap())
      node.isArray -> EnumArrayValueWrapper(node.elements().asSequence().mapNotNull(JacksonSchemaNodeAccessor::readAnything).toList().toTypedArray())
      else -> null
    }
  }

  private fun asDoubleQuotedTextOrNull(jsonNode: JsonNode): String? {
    if (!jsonNode.isTextual) return null
    return jsonNode.asText().asDoubleQuotedString()
  }

  private fun escapeAndCompileJsonPointer(unescapedPointer: String): JsonPointer? {
    if (!fastCheckIfCorrectPointer(unescapedPointer)) return null

    return try {
      JsonPointer.compile(adaptJsonPointerToJacksonImplementation(unescapedPointer))
    }
    catch (exception: IllegalArgumentException) {
      Logger.getInstance("jsonSchemaParsingUtils").warn("Unable to compile json pointer. Resolve aborted.", exception)
      null
    }
  }

  private fun fastCheckIfCorrectPointer(maybeIncorrectPointer: String): Boolean {
    return maybeIncorrectPointer.startsWith("/")
  }

  private fun adaptJsonPointerToJacksonImplementation(oldPointer: String): String {
    return when (oldPointer) {
      "/" -> ""
      else -> URLUtil.unescapePercentSequences(oldPointer)
    }
  }
}

internal fun String.asUnquotedString(): String {
  return StringUtil.unquoteString(this)
}

internal fun String.asDoubleQuotedString(): String {
  return this.asUnquotedString().let { "\"$it\"" }
}

internal fun escapeForbiddenJsonPointerSymbols(pointerSegment: String): String {
  return pointerSegment.replace("~", "~0").replace("/", "~1")
}