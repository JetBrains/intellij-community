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
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = Logger.getInstance("#com.intellij.json.networknt.wrapper.PsiToJsonNodeConverter")
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

private fun convertString(adapter: JsonValueAdapter, walker: JsonLikePsiWalker): JsonNode? {
  val raw = getNodeTextForConversion(adapter, walker)
  val isSingleQuoted = raw.length >= 2 && raw.startsWith('\'') && raw.endsWith('\'')
  if (isSingleQuoted && !walker.allowsSingleQuotes()) return null
  val unquoted = if (walker.requiresValueQuotes()) {
    when {
      raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"') -> raw.substring(1, raw.length - 1)
      raw.length >= 2 && raw.startsWith('\'') && raw.endsWith('\'') -> raw.substring(1, raw.length - 1)
      else -> null
    }
  }
  else {
    null
  }
  val unescaped = if (unquoted != null) {
    if (walker.requiresValueQuotes() && walker.allowsSingleQuotes()) unescapeJson5String(unquoted)
    else unescapeJsonString(unquoted)
  }
  else raw
  return JsonNodeFactory.instance.stringNode(unescaped)
}

private fun convertBoolean(adapter: JsonValueAdapter, walker: JsonLikePsiWalker): JsonNode {
  val text = getNodeTextForConversion(adapter, walker).trim()
  return JsonNodeFactory.instance.booleanNode(text.equals("true", ignoreCase = true))
}

private val INT_RANGE = BigInteger.valueOf(Int.MIN_VALUE.toLong())..BigInteger.valueOf(Int.MAX_VALUE.toLong())
private val LONG_RANGE = BigInteger.valueOf(Long.MIN_VALUE)..BigInteger.valueOf(Long.MAX_VALUE)

// https://spec.json5.org/#numbers
private fun json5NonFiniteDoubleOrNull(text: String): Double? = when (text) {
  "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY
  "-Infinity" -> Double.NEGATIVE_INFINITY
  "NaN", "+NaN", "-NaN" -> Double.NaN
  else -> null
}

private fun convertNumber(adapter: JsonValueAdapter, walker: JsonLikePsiWalker): JsonNode? {
  val text = getNodeTextForConversion(adapter, walker).trim()
  // Standard JSON has no Infinity/NaN literals (RFC 8259) — allowsSingleQuotes() is this
  // file's existing signal for "JSON5 dialect" (see convertString), reused here so a plain
  // .json file keeps rejecting these tokens as numbers.
  if (walker.allowsSingleQuotes()) {
    json5NonFiniteDoubleOrNull(text)?.let { return JsonNodeFactory.instance.numberNode(it) }
  }
  return try {
    val unsigned = if (text.startsWith('-') || text.startsWith('+')) text.substring(1) else text
    val isDecimalRadix = !unsigned.startsWith("0x", ignoreCase = true) &&
                         !unsigned.startsWith("0o", ignoreCase = true) &&
                         !unsigned.startsWith("0b", ignoreCase = true)
    if (isDecimalRadix && ('.' in text || 'e' in text || 'E' in text)) {
      JsonNodeFactory.instance.numberNode(BigDecimal(text))
    }
    else {
      val integer = parseInteger(text)
      when {
        integer in INT_RANGE -> JsonNodeFactory.instance.numberNode(integer.toInt())
        integer in LONG_RANGE -> JsonNodeFactory.instance.numberNode(integer.toLong())
        else -> JsonNodeFactory.instance.numberNode(integer)
      }
    }
  }
  catch (_: NumberFormatException) {
    // Do not invent a numeric value for syntax extensions unsupported by Jackson.
    null
  }
}

private fun parseInteger(text: String): BigInteger {
  var digits = text
  val negative = digits.startsWith('-')
  if (negative || digits.startsWith('+')) {
    digits = digits.substring(1)
  }

  val radix = when {
    digits.startsWith("0x", ignoreCase = true) -> 16
    digits.startsWith("0o", ignoreCase = true) -> 8
    digits.startsWith("0b", ignoreCase = true) -> 2
    else -> 10
  }
  if (radix != 10) {
    digits = digits.substring(2)
  }

  digits = digits.replace("_", "")
  val integer = BigInteger(digits, radix)
  return if (negative) integer.negate() else integer
}

private class YamlTextValueMethodCache(val classLoader: ClassLoader, val method: Method?)

@Volatile
private var yamlTextValueMethodCache: YamlTextValueMethodCache? = null
private val loggedYamlReflectionFailure = AtomicBoolean(false)

private fun getNodeTextForConversion(adapter: JsonValueAdapter, walker: JsonLikePsiWalker): String {
  val raw = walker.getNodeTextForValidation(adapter.delegate)
  if (walker.requiresValueQuotes()) return raw

  // YAML's public validation-text API intentionally returns source syntax because legacy
  // validation unquotes it itself. Networknt needs the decoded scalar value, so use the PSI
  // interface only here without changing that shared API or adding a hard YAML dependency.
  val delegate = adapter.delegate
  val classLoader = delegate.javaClass.classLoader
  var cache = yamlTextValueMethodCache
  if (cache == null || cache.classLoader !== classLoader) {
    cache = YamlTextValueMethodCache(classLoader, resolveYamlGetTextValueMethod(classLoader))
    yamlTextValueMethodCache = cache
  }
  val method = cache.method ?: return raw
  if (!method.declaringClass.isInstance(delegate)) return raw
  return runCatching { method.invoke(delegate) as? String }
    .onFailure { LOG.warn("Failed to decode YAML scalar value via reflection; falling back to raw source text", it) }
    .getOrNull() ?: raw
}

private fun resolveYamlGetTextValueMethod(classLoader: ClassLoader): Method? =
  runCatching {
    Class.forName("org.jetbrains.yaml.psi.YAMLScalar", false, classLoader).getMethod("getTextValue")
  }.onFailure {
    if (loggedYamlReflectionFailure.compareAndSet(false, true)) {
      LOG.warn("YAMLScalar.getTextValue() unavailable via reflection; YAML scalar decoding falls back to raw source text", it)
    }
  }.getOrNull()

// Uses Jackson (the same engine networknt uses to parse schemas) for quoted JSON values.
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

private fun unescapeJson5String(s: String): String {
  val result = StringBuilder(s.length)
  var index = 0
  while (index < s.length) {
    val current = s[index]
    if (current != '\\' || index + 1 >= s.length) {
      result.append(current)
      index++
      continue
    }

    val escaped = s[index + 1]
    when (escaped) {
      '\'', '"', '\\', '/' -> result.append(escaped).also { index += 2 }
      'b' -> result.append('\b').also { index += 2 }
      'f' -> result.append('\u000C').also { index += 2 }
      'n' -> result.append('\n').also { index += 2 }
      'r' -> result.append('\r').also { index += 2 }
      't' -> result.append('\t').also { index += 2 }
      'v' -> result.append('\u000B').also { index += 2 }
      '0' -> result.append('\u0000').also { index += 2 }
      'x' -> {
        val end = index + 4
        val value = if (end <= s.length) s.substring(index + 2, end).toIntOrNull(16) else null
        if (value != null) {
          result.append(value.toChar())
          index = end
        }
        else {
          result.append('\\').append(escaped)
          index += 2
        }
      }
      'u' -> {
        val end = index + 6
        val value = if (end <= s.length) s.substring(index + 2, end).toIntOrNull(16) else null
        if (value != null) {
          result.append(value.toChar())
          index = end
        }
        else {
          result.append('\\').append(escaped)
          index += 2
        }
      }
      '\n' -> index += 2
      '\r' -> {
        index += if (index + 2 < s.length && s[index + 2] == '\n') 3 else 2
      }
      '\u2028', '\u2029' -> index += 2
      else -> {
        result.append(escaped)
        index += 2
      }
    }
  }
  return result.toString()
}
