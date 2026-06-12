// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import java.math.BigDecimal

private val LOG = Logger.getInstance("com.intellij.json.networknt.wrapper.PsiToJsonNodeConverter")

private val UNESCAPE_JSON_FACTORY = JsonFactory()

/**
 * Picks which value of a [JsonPropertyAdapter] to encode into the resulting JsonNode tree.
 * The default behaviour is [defaultPropertyValueSelector], which takes the first value (matching
 * the legacy walker's primary path). Branch-extension passes use a custom selector that swaps
 * the chosen branch for one specific [JsonPropertyAdapter] (keyed by `getDelegate`).
 */
internal typealias PropertyValueSelector = (JsonPropertyAdapter) -> JsonValueAdapter?

internal val defaultPropertyValueSelector: PropertyValueSelector = { it.values.firstOrNull() }

/**
 * Converts a PSI tree to a Jackson [JsonNode] tree using the language-agnostic
 * [JsonLikePsiWalker] / [JsonValueAdapter] abstraction.
 *
 * This enables networknt validation for any language that provides a PSI walker
 * (JSON, YAML, TOML, etc.) without re-parsing the text through Jackson.
 */
fun convertPsiToJsonNode(
  walker: JsonLikePsiWalker?,
  rootElement: PsiElement?,
): JsonNode? = convertPsiToJsonNode(walker, rootElement, defaultPropertyValueSelector)

internal fun convertPsiToJsonNode(
  walker: JsonLikePsiWalker?,
  rootElement: PsiElement?,
  selector: PropertyValueSelector,
): JsonNode? {
  if (walker == null || rootElement == null) return null

  // For PsiFile, use walker.getRoots() to get the proper root value element.
  // TODO: workaround for YamlJsonPsiWalker.createValueAdapter(YAMLDocument) bug:
  //  it uses getFirstChild() which returns the DOCUMENT_MARKER ("---") token
  //  instead of getTopLevelValue(). Once fixed in platform, this PsiFile check
  //  can be removed — createValueAdapter will handle YAMLDocument correctly.
  val effectiveRoot = if (rootElement is PsiFile) {
    walker.getRoots(rootElement)?.firstOrNull() ?: rootElement
  }
  else {
    rootElement
  }

  val adapter = walker.createValueAdapter(effectiveRoot) ?: return null
  return convertValue(adapter, walker, selector)
}

private fun convertValue(adapter: JsonValueAdapter, walker: JsonLikePsiWalker, selector: PropertyValueSelector): JsonNode? {
  // Adapters that opt out of value-level validation (JS reference/call/new expressions —
  // see JSJsonForeignValueAdapter) must not be validated by networknt as concrete values:
  // their text is opaque (e.g. `getMode()` is a function call, not the string "getMode()").
  // Emit a placeholder; [PsiLocationIndex] marks the PSI as suppressed so any error
  // networknt attributes to it is dropped post-validation by [NetworkntErrorMapper].
  if (!adapter.shouldCheckAsValue()) {
    return JsonNodeFactory.instance.nullNode()
  }
  return when {
    adapter.isObject -> {
      val asObject = adapter.asObject ?: return null
      convertObject(asObject, walker, selector)
    }
    adapter.isArray -> {
      val asArray = adapter.asArray ?: return null
      convertArray(asArray, walker, selector)
    }
    adapter.isNull -> JsonNodeFactory.instance.nullNode()
    adapter.isBooleanLiteral -> convertBoolean(adapter, walker)
    adapter.isNumberLiteral -> convertNumber(adapter, walker)
    adapter.isStringLiteral -> convertString(adapter, walker)
    else -> {
      // Adapter reports no primitive type. If it has a structural array/object face
      // but denied being one (e.g. YAMLSequence / YAMLMapping with an unrecognised !tag —
      // YamlArrayAdapter/YamlObjectAdapter return false from isArray/isObject to force a
      // schema type mismatch), emit an ObjectNode so networknt produces a clean type error
      // rather than silently matching a string branch via the raw-text fallback below.
      if (adapter.asArray != null || adapter.asObject != null) {
        JsonNodeFactory.instance.objectNode()
      }
      else {
        val text = walker.getNodeTextForValidation(adapter.delegate)
        JsonNodeFactory.instance.stringNode(text)
      }
    }
  }
}

private fun convertObject(objectAdapter: JsonObjectValueAdapter, walker: JsonLikePsiWalker, selector: PropertyValueSelector): JsonNode {
  val objectNode = JsonNodeFactory.instance.objectNode()
  for (property in objectAdapter.propertyList) {
    val name = property.name ?: continue
    val valueAdapter = selector(property) ?: continue
    val valueNode = convertValue(valueAdapter, walker, selector) ?: JsonNodeFactory.instance.nullNode()
    objectNode.set(name, valueNode)
  }
  return objectNode
}

private fun convertArray(arrayAdapter: JsonArrayValueAdapter, walker: JsonLikePsiWalker, selector: PropertyValueSelector): JsonNode {
  val arrayNode = JsonNodeFactory.instance.arrayNode()
  for (element in arrayAdapter.elements) {
    val elementNode = convertValue(element, walker, selector) ?: JsonNodeFactory.instance.nullNode()
    arrayNode.add(elementNode)
  }
  return arrayNode
}

private fun convertString(adapter: JsonValueAdapter, walker: JsonLikePsiWalker): JsonNode {
  val raw = walker.getNodeTextForValidation(adapter.delegate)
  val unquoted = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
    raw.substring(1, raw.length - 1)
  }
  else if (raw.length >= 2 && raw.startsWith('\'') && raw.endsWith('\'')) {
    raw.substring(1, raw.length - 1)
  }
  else {
    raw
  }
  val unescaped = unescapeJsonString(unquoted)
  return JsonNodeFactory.instance.stringNode(unescaped)
}

private fun convertBoolean(adapter: JsonValueAdapter, walker: JsonLikePsiWalker): JsonNode {
  val text = walker.getNodeTextForValidation(adapter.delegate).trim()
  return JsonNodeFactory.instance.booleanNode(text == "true")
}

private fun convertNumber(adapter: JsonValueAdapter, walker: JsonLikePsiWalker): JsonNode {
  val text = walker.getNodeTextForValidation(adapter.delegate).trim()
  return try {
    if ('.' in text || 'e' in text || 'E' in text) {
      val bd = BigDecimal(text)
      JsonNodeFactory.instance.numberNode(bd)
    }
    else {
      val longVal = java.lang.Long.parseLong(text)
      if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) {
        JsonNodeFactory.instance.numberNode(longVal.toInt())
      }
      else {
        JsonNodeFactory.instance.numberNode(longVal)
      }
    }
  }
  catch (e: NumberFormatException) {
    LOG.error("Failed to parse number from PSI text: '$text'", e)
    JsonNodeFactory.instance.numberNode(0)
  }
}

// Uses Jackson (same engine networknt uses to parse schemas), so values seen by the validator
// and by us are guaranteed to match on JSON spec escapes.
//
// Correct for JSON PSI and YAML double-quoted scalars in the common case. Semantically wrong for:
//   - YAML single-quoted ('it''s' → should be it's)
//   - YAML plain / block / folded scalars with literal backslashes
//   - YAML-extended escapes outside JSON spec (\0, \a, \e, \x..)
// When YAML is wired into networknt.wrapper classpath, migrate to a language-aware
// JsonValueAdapter.getStringValue() so each PSI language can plug in its own unescape rules.
private fun unescapeJsonString(s: String): String {
  if ('\\' !in s) return s
  return try {
    UNESCAPE_JSON_FACTORY.createParser(ObjectReadContext.empty(), "\"$s\"").use { parser ->
      parser.nextToken()
      parser.string
    }
  }
  catch (_: Exception) {
    s
  }
}
