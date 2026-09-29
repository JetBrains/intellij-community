@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The quoted whole-leaf token of the chain's platform id. `dev_plugin_file_graph` substitutes the same spelling. */
private const val PLATFORM_LEAF_TOKEN = "\"{platform}\""

/** Every token starts with this text. No record text may hold it quoted, and no slot value may hold it at all. */
private const val TOKEN_PREFIX = "{platform"

/** A slot name is a JSON member key. */
private val SLOT_NAME = Regex("[A-Za-z][A-Za-z0-9]*")

private val JSON_WHITESPACE = setOf(' ', '\t', '\n', '\r')

/** The outcome of folding the per-platform plan texts of one plugin into one tokenized body. */
sealed interface DevDistPluginPlanFold {
  /**
   * One body serves every platform. [valuesByPlatform] keeps the input platform order, and each platform holds every
   * slot in document order. [resolvePluginPlanText] with a platform and its values gives back that platform's record
   * text byte for byte.
   */
  class Folded(
    @JvmField val body: String,
    @JvmField val valuesByPlatform: Map<String, Map<String, String>>,
  ) : DevDistPluginPlanFold {
    /** The slot names in document order. Every platform states the same slots. */
    val slotNames: Set<String>
      get() = valuesByPlatform.values.first().keys
  }

  /**
   * The records of [platformA] and [platformB] differ at [path] in JSON shape or in a non-string leaf. Or the string
   * leaf at [path] holds a second distinct value tuple under a key that already names a slot.
   */
  class Refused(
    @JvmField val path: String,
    @JvmField val platformA: String,
    @JvmField val platformB: String,
    @JvmField val reason: String,
  ) : DevDistPluginPlanFold
}

/**
 * Folds the plan texts of one plugin, one per platform in `HOST_PLATFORMS` order, into one tokenized body.
 *
 * A string leaf that holds the platform id on every platform becomes `"{platform}"`. A string leaf that differs across
 * the platforms becomes a slot `"{platform:<name>}"`. Equal value tuples share one slot. The name is the member key of
 * the leaf, or the key of the nearest enclosing array. A key names one slot, so a second distinct tuple under the key
 * refuses the fold. The body is the first platform's text with those leaves replaced in place. Nothing is re-encoded.
 *
 * The records are compared as file text, the operations included, because the frozen layout configurations have no
 * structural equality.
 *
 * The function proves that every platform's text resolves back byte for byte. It throws on a mismatch. It throws on a
 * quoted token inside a record text. It throws on a slot value that JSON would escape or that holds a token.
 * A shape difference, a differing non-string leaf or a reused slot key refuses the fold and names the first such path.
 */
fun foldDevDistPluginPlanTexts(textsByPlatform: LinkedHashMap<String, String>): DevDistPluginPlanFold {
  require(textsByPlatform.size >= 2) { "A fold needs two or more platform records" }
  val platforms = textsByPlatform.keys.toList()
  for ((platform, text) in textsByPlatform) checkPlanTextHoldsNoToken(text, platform)
  val trees = platforms.map { Json.parseToJsonElement(textsByPlatform.getValue(it)) }
  val leaves = ArrayList<StringLeaf>()
  walkInLockstep(trees, platforms, path = "$", key = null, leaves)?.let { return it }

  val tokens = arrayOfNulls<String>(leaves.size)
  val slotByTuple = HashMap<List<String>, String>()
  val tupleBySlot = LinkedHashMap<String, List<String>>()
  for ((index, leaf) in leaves.withIndex()) {
    val values = leaf.values
    if (values.distinct().size == 1) continue
    if (values == platforms) {
      tokens[index] = PLATFORM_LEAF_TOKEN
      continue
    }
    val name = slotByTuple.get(values) ?: leaf.key.also { name ->
      if (tupleBySlot.containsKey(name)) {
        val other = values.indices.first { values.get(it) != values.first() }
        return DevDistPluginPlanFold.Refused(leaf.path, platforms.first(), platforms.get(other), "a distinct value tuple reuses the slot key '$name'")
      }
      check(SLOT_NAME.matches(name)) { "The member key '$name' at ${leaf.path} is not a slot name" }
      for ((platform, value) in platforms.zip(values)) checkSlotValue(value, name, platform)
      slotByTuple.put(values, name)
      tupleBySlot.put(name, values)
    }
    tokens[index] = "\"{platform:$name}\""
  }

  val valuesByPlatform = platforms.withIndex().associate { (index, platform) -> platform to tupleBySlot.mapValues { it.value.get(index) } }
  val body = spliceStringValues(textsByPlatform.getValue(platforms.first()), tokens)
  for ((platform, values) in valuesByPlatform) {
    val expected = textsByPlatform.getValue(platform)
    val resolved = resolvePluginPlanText(body, platform, values)
    check(resolved == expected) {
      val offset = resolved.commonPrefixWith(expected).toByteArray(Charsets.UTF_8).size
      "The folded body does not resolve to the record of $platform: first difference at byte $offset"
    }
  }
  return DevDistPluginPlanFold.Folded(body, valuesByPlatform)
}

/**
 * Fails when the record text of [subject] holds a quoted token. The fold checks every input text.
 * [DevDistPluginPlanFiles.collect] checks the plan file of a neutral record and of a refused platform record at
 * emission. So no unresolved token reaches an executor.
 */
internal fun checkPlanTextHoldsNoToken(text: String, subject: String) {
  check("\"$TOKEN_PREFIX" !in text) { "The record text of $subject contains a platform token" }
}

/**
 * Resolves a tokenized plan text for one platform. This is the quoted whole-leaf replacement that
 * `dev_plugin_file_graph` performs with `expand_template`. `"{platform}"` becomes the quoted [platform].
 * `"{platform:<name>}"` becomes the quoted value of that slot in [values]. The order is irrelevant, because no value
 * may hold a token.
 */
fun resolvePluginPlanText(body: String, platform: String, values: Map<String, String>): String {
  var result = body.replace(PLATFORM_LEAF_TOKEN, "\"$platform\"")
  for ((name, value) in values) {
    result = result.replace("\"{platform:$name}\"", "\"$value\"")
  }
  return result
}

/** One string leaf of the lockstep walk: its JSON path, its slot key, and its value on every platform in order. */
private class StringLeaf(
  @JvmField val path: String,
  @JvmField val key: String,
  @JvmField val values: List<String>,
)

/**
 * Walks the trees of every platform in lockstep and collects the string leaves in document order. The first node whose
 * shape or non-string value differs between the first platform and another one refuses the fold. [key] is the member
 * key of the node, or the key of the nearest enclosing array for an array item.
 */
private fun walkInLockstep(
  nodes: List<JsonElement>,
  platforms: List<String>,
  path: String,
  key: String?,
  leaves: MutableList<StringLeaf>,
): DevDistPluginPlanFold.Refused? {
  fun refused(index: Int, reason: String) = DevDistPluginPlanFold.Refused(path, platforms.first(), platforms.get(index), reason)

  when (val first = nodes.first()) {
    is JsonObject -> {
      val keys = first.keys.toList()
      for (index in 1 until nodes.size) {
        val node = nodes.get(index)
        if (node !is JsonObject || node.keys.toList() != keys) return refused(index, "the object members differ")
      }
      for (memberKey in keys) {
        walkInLockstep(nodes.map { (it as JsonObject).getValue(memberKey) }, platforms, "$path.$memberKey", memberKey, leaves)?.let { return it }
      }
    }
    is JsonArray -> {
      for (index in 1 until nodes.size) {
        val node = nodes.get(index)
        if (node !is JsonArray || node.size != first.size) return refused(index, "the array lengths differ")
      }
      for (item in first.indices) {
        walkInLockstep(nodes.map { (it as JsonArray).get(item) }, platforms, "$path[$item]", key, leaves)?.let { return it }
      }
    }
    is JsonPrimitive -> {
      for (index in 1 until nodes.size) {
        val node = nodes.get(index)
        if (node !is JsonPrimitive || node.isString != first.isString) return refused(index, "a string leaf meets another kind")
        if (!first.isString && node.content != first.content) return refused(index, "the non-string leaves differ")
      }
      if (first.isString) {
        leaves.add(StringLeaf(path, requireNotNull(key) { "A string leaf at $path has no member key" }, nodes.map { (it as JsonPrimitive).content }))
      }
    }
  }
  return null
}

private fun checkSlotValue(value: String, name: String, platform: String) {
  check(value.none { it == '"' || it == '\\' || it < ' ' }) { "The value of slot '$name' on $platform needs a JSON escape: $value" }
  check(TOKEN_PREFIX !in value) { "The value of slot '$name' on $platform contains a platform token: $value" }
}

/** The `[start, end)` range of one string token of a JSON text. */
private class StringToken(@JvmField val start: Int, @JvmField val end: Int)

/**
 * Replaces the string values of [text] whose entry in [tokens] is not null by that token text. The text keeps every other
 * byte. The number of string values must equal the number of string leaves the tree walk found.
 */
private fun spliceStringValues(text: String, tokens: Array<String?>): String {
  val values = scanStringValues(text)
  check(values.size == tokens.size) { "The text has ${values.size} string values and the tree has ${tokens.size} string leaves" }
  val result = StringBuilder(text.length)
  var position = 0
  for ((index, value) in values.withIndex()) {
    val token = tokens[index] ?: continue
    result.append(text, position, value.start).append(token)
    position = value.end
  }
  result.append(text, position, text.length)
  return result.toString()
}

/** The string values of a JSON text in document order. A string that a `:` follows is a member key and is skipped. */
private fun scanStringValues(text: String): List<StringToken> {
  val result = ArrayList<StringToken>()
  var index = 0
  while (index < text.length) {
    if (text[index] != '"') {
      index++
      continue
    }
    val start = index
    index++
    while (true) {
      check(index < text.length) { "The JSON text has an unterminated string at $start" }
      val c = text[index]
      index += if (c == '\\') 2 else 1
      if (c == '"') break
    }
    val end = index
    var next = index
    while (next < text.length && text[next] in JSON_WHITESPACE) next++
    if (next >= text.length || text[next] != ':') result.add(StringToken(start, end))
  }
  return result
}
