// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

/**
 * Reads one plugin's content residue back out of the `dev_dist_plugin` call in the plugin's own `BUILD.bazel`.
 *
 * The residue sits on the call that this generator itself rewrites, so the generator has to read what the previous run
 * wrote. `emitDevDistPlugin` is the writer, this is the reader, and [verifyDevDistPluginResidueReadBack] holds the two
 * together on every run of every plugin.
 *
 * **Every doubt is a failure.** A call this file cannot parse names the plugin and stops the run. A reader that answered
 * an empty residue instead would erase the plugin's stated facts on the next write, and a second run would then reach a
 * fixed point on the erased tree. So no path here answers a default for input it did not understand.
 *
 * A tokenizer, and not a line reader. `buildifier` writes a list of one item inline and a longer list one item per
 * line, and it can reflow either one at any time. A shape rule here would be a second copy of the formatter's rules,
 * and this file would then refuse text the formatter is free to write.
 */

/** See `dev_dist_plugin.residue_lib_root_jars`. */
internal const val RESIDUE_LIB_ROOT_JARS: String = "residue_lib_root_jars"

/** See `dev_dist_plugin.residue_member_jars`. */
internal const val RESIDUE_MEMBER_JARS: String = "residue_member_jars"

/** See `dev_dist_plugin.residue_merged_libraries`. */
internal const val RESIDUE_MERGED_LIBRARIES: String = "residue_merged_libraries"

/** See `dev_dist_plugin.residue_module_libraries`. */
internal const val RESIDUE_MODULE_LIBRARIES: String = "residue_module_libraries"

/** See `dev_dist_plugin.residue_project_libraries`. */
internal const val RESIDUE_PROJECT_LIBRARIES: String = "residue_project_libraries"

/** See `dev_dist_plugin.residue_raw_members`. */
internal const val RESIDUE_RAW_MEMBERS: String = "residue_raw_members"

/** See `dev_dist_plugin.residue_separate_jars`. */
internal const val RESIDUE_SEPARATE_JARS: String = "residue_separate_jars"

/** See `dev_dist_plugin.residue_vetoed_members`. */
internal const val RESIDUE_VETOED_MEMBERS: String = "residue_vetoed_members"

/** A section that states nothing, so that an absent residue and an all-default one are one verdict. */
internal val EMPTY_CONTENT_RESIDUE: ContentResidueSection = ContentResidueSection()

/**
 * The text of the `dev <main module>` section of [fileContent], or `null` when the file holds no such section.
 *
 * `BazelFileUpdater` keeps `originalContent`, which is what the previous run wrote, and `removeSections("dev ")` takes
 * the section out of the content it is about to rewrite. So the previous section is in `originalContent` alone, and this
 * is the reader of it.
 */
internal fun devDistPluginSectionText(fileContent: String, mainModuleName: String): String? {
  val startToken = "### auto-generated section `dev $mainModuleName` start"
  val endToken = "### auto-generated section `dev $mainModuleName` end"
  val start = fileContent.indexOf(startToken)
  if (start < 0) {
    return null
  }
  val end = fileContent.indexOf(endToken, start)
  check(end >= 0) { "'$mainModuleName' opens a `dev` section and does not close it" }
  return fileContent.substring(start + startToken.length, end)
}

/**
 * The content residue the `dev_dist_plugin` call of [section] states, or `null` when it states none.
 *
 * `null` for a section with no call, and for a call that states no residue attribute. Both mean pure convention, which
 * is the verdict an absent `dev-dist.yaml` gives.
 *
 * [plugin] is named in every failure, because a failure here is about one plugin's own file.
 */
internal fun parseDevDistPluginResidue(section: String, plugin: String): ContentResidueSection? {
  val arguments = devDistPluginArguments(section = section, plugin = plugin) ?: return null
  if (RESIDUE_ATTRIBUTES.none { arguments.containsKey(it) }) {
    return null
  }
  val projectLibraries = arguments.nameList(plugin = plugin, attribute = RESIDUE_PROJECT_LIBRARIES)
  val moduleLibraries = arguments.nameListDict(plugin = plugin, attribute = RESIDUE_MODULE_LIBRARIES)
  return ContentResidueSection(
    libRootJars = arguments.nameList(plugin = plugin, attribute = RESIDUE_LIB_ROOT_JARS),
    rawMembers = arguments.nameList(plugin = plugin, attribute = RESIDUE_RAW_MEMBERS),
    vetoedMembers = arguments.nameList(plugin = plugin, attribute = RESIDUE_VETOED_MEMBERS),
    separateJars = arguments.nameList(plugin = plugin, attribute = RESIDUE_SEPARATE_JARS),
    memberJars = arguments.nameListDict(plugin = plugin, attribute = RESIDUE_MEMBER_JARS),
    mergedLibraries = arguments.nameListDict(plugin = plugin, attribute = RESIDUE_MERGED_LIBRARIES),
    // The order the two attributes can restate, and the order `emitDevDistPlugin` refuses to write anything else in:
    // a project row has no module, so it sorts before every module row.
    libraries = projectLibraries.map { ResidueLibraryRow(name = it) } +
                moduleLibraries.entries.flatMap { (module, names) -> names.map { ResidueLibraryRow(module = module, name = it) } },
  )
}

/**
 * Fails unless the residue of [call] reads back as [residue].
 *
 * The guard the whole move needs. Once the residue lives in the file the generator rewrites, a reader that loses a fact
 * writes the loss out, and the next run agrees with itself - so a two-run fixed point proves nothing about it. This
 * compares what one run is about to write against what the plugin states, for every plugin, on every run.
 */
internal fun verifyDevDistPluginResidueReadBack(plugin: String, residue: ContentResidueSection?, call: String) {
  val stated = residue?.takeIf { it != EMPTY_CONTENT_RESIDUE }
  val readBack = parseDevDistPluginResidue(section = call, plugin = plugin)
  check(readBack == stated) {
    "$plugin: the `dev_dist_plugin` call does not read back as the residue it states.\nStated: $stated\nRead back: $readBack\nCall:\n$call"
  }
}

/** Every attribute of the call that carries a [ContentResidueSection] field. */
private val RESIDUE_ATTRIBUTES: List<String> = listOf(
  RESIDUE_LIB_ROOT_JARS,
  RESIDUE_MEMBER_JARS,
  RESIDUE_MERGED_LIBRARIES,
  RESIDUE_MODULE_LIBRARIES,
  RESIDUE_PROJECT_LIBRARIES,
  RESIDUE_RAW_MEMBERS,
  RESIDUE_SEPARATE_JARS,
  RESIDUE_VETOED_MEMBERS,
)

private const val CALL_HEAD: String = "dev_dist_plugin("

/**
 * The named arguments of the one `dev_dist_plugin` call of [section], or `null` when the section holds no call.
 *
 * Anchored at the start of a line, which is where a target of a generated section stands. So neither the load statement
 * naming the symbol nor a `dev_dist_plugin_jar` target of the same section can be taken for the call.
 */
private fun devDistPluginArguments(section: String, plugin: String): Map<String, StarlarkValue>? {
  var head = -1
  var from = 0
  while (true) {
    val at = section.indexOf(CALL_HEAD, from)
    if (at < 0) {
      break
    }
    from = at + CALL_HEAD.length
    if (at != 0 && section[at - 1] != '\n') {
      continue
    }
    check(head < 0) { "$plugin: two `dev_dist_plugin` calls in one section, and a plugin states its residue once" }
    head = at
  }
  if (head < 0) {
    return null
  }
  val open = head + CALL_HEAD.length - 1
  val reader = StarlarkReader(text = section, plugin = plugin, index = open + 1)
  return reader.readArguments()
}

/** One value a call states. [StarlarkOpaque] is anything this file does not interpret, which every residue attribute refuses. */
private sealed interface StarlarkValue

private class StarlarkText(@JvmField val value: String) : StarlarkValue

private class StarlarkList(@JvmField val items: List<StarlarkValue>) : StarlarkValue

private class StarlarkDict(@JvmField val entries: List<Pair<StarlarkValue, StarlarkValue>>) : StarlarkValue

private object StarlarkOpaque : StarlarkValue

/**
 * Reads a `name = value` argument list, and the values a residue attribute can take.
 *
 * Deliberately small: a string, a list, a dict, and [StarlarkOpaque] for everything else. An attribute this generator does not
 * own can hold anything, and skipping it must not need a grammar for it - but a residue attribute that is [StarlarkOpaque] is a
 * failure, so nothing is guessed either.
 */
private class StarlarkReader(private val text: String, private val plugin: String, private var index: Int) {
  fun readArguments(): Map<String, StarlarkValue> {
    val result = LinkedHashMap<String, StarlarkValue>()
    while (true) {
      skipTrivia()
      if (atEnd() || text[index] == ')') {
        return result
      }
      val name = readName()
      skipTrivia()
      expect('=')
      skipTrivia()
      val value = readValue()
      if (result.put(name, value) != null) {
        refuse("states `$name` twice")
      }
      skipTrivia()
      if (!atEnd() && text[index] == ',') {
        index++
      }
      else if (!atEnd() && text[index] != ')') {
        refuse("expects `,` or `)` after `$name`, and reads ${here()}")
      }
    }
  }

  private fun readName(): String {
    val start = index
    while (!atEnd() && (text[index] == '_' || text[index].isLetterOrDigit())) {
      index++
    }
    if (start == index) {
      refuse("expects an attribute name, and reads ${here()}")
    }
    return text.substring(start, index)
  }

  private fun readValue(): StarlarkValue {
    if (atEnd()) {
      refuse("ends inside the `dev_dist_plugin` call")
    }
    return when (text[index]) {
      '"' -> StarlarkText(readText())
      '[' -> StarlarkList(readSequence())
      '{' -> StarlarkDict(readTable())
      else -> {
        skipOpaque()
        StarlarkOpaque
      }
    }
  }

  private fun readText(): String {
    index++
    val builder = StringBuilder()
    while (true) {
      if (atEnd()) {
        refuse("ends inside a string")
      }
      when (val c = text[index]) {
        '"' -> {
          index++
          return builder.toString()
        }
        '\\' -> {
          index++
          if (atEnd()) {
            refuse("ends inside a string")
          }
          builder.append(text[index])
          index++
        }
        '\n' -> refuse("holds a line break inside a string")
        else -> {
          builder.append(c)
          index++
        }
      }
    }
  }

  private fun readSequence(): List<StarlarkValue> {
    index++
    val result = ArrayList<StarlarkValue>()
    while (true) {
      skipTrivia()
      if (atEnd()) {
        refuse("ends inside a list")
      }
      if (text[index] == ']') {
        index++
        return result
      }
      result.add(readValue())
      skipTrivia()
      if (!atEnd() && text[index] == ',') {
        index++
      }
    }
  }

  private fun readTable(): List<Pair<StarlarkValue, StarlarkValue>> {
    index++
    val result = ArrayList<Pair<StarlarkValue, StarlarkValue>>()
    while (true) {
      skipTrivia()
      if (atEnd()) {
        refuse("ends inside a dict")
      }
      if (text[index] == '}') {
        index++
        return result
      }
      val key = readValue()
      skipTrivia()
      expect(':')
      skipTrivia()
      result.add(key to readValue())
      skipTrivia()
      if (!atEnd() && text[index] == ',') {
        index++
      }
    }
  }

  /**
   * Steps over a value this file does not interpret, up to the token that ends it.
   *
   * Brackets are counted and strings are read, so a nested call or a nested list of another attribute cannot end the
   * value early. A `:` ends it too, so an opaque *key* of a dict stops at its own colon and the dict reader can still
   * name the key it refuses.
   */
  private fun skipOpaque() {
    var depth = 0
    while (!atEnd()) {
      when (text[index]) {
        '"' -> {
          readText()
          continue
        }
        '#' -> {
          skipTrivia()
          continue
        }
        '(', '[', '{' -> depth++
        ')', ']', '}' -> {
          if (depth == 0) {
            return
          }
          depth--
        }
        ',', ':' -> if (depth == 0) return
      }
      index++
    }
    refuse("ends inside the `dev_dist_plugin` call")
  }

  private fun skipTrivia() {
    while (!atEnd()) {
      val c = text[index]
      if (c.isWhitespace()) {
        index++
      }
      else if (c == '#') {
        while (!atEnd() && text[index] != '\n') {
          index++
        }
      }
      else {
        return
      }
    }
  }

  private fun expect(c: Char) {
    if (atEnd() || text[index] != c) {
      refuse("expects `$c`, and reads ${here()}")
    }
    index++
  }

  private fun atEnd(): Boolean = index >= text.length

  private fun here(): String = if (atEnd()) "the end of the section" else "`${text.substring(index, minOf(text.length, index + 20))}`"

  private fun refuse(what: String): Nothing = error("$plugin: the `dev_dist_plugin` call $what")
}

/** One residue attribute as a name list, or an empty list when the call states none. */
private fun Map<String, StarlarkValue>.nameList(plugin: String, attribute: String): List<String> {
  val value = get(attribute) ?: return emptyList()
  if (value !is StarlarkList) {
    refuse(plugin = plugin, attribute = attribute, what = "is not a list")
  }
  if (value.items.isEmpty()) {
    // The writer leaves an empty field out, so an empty list is a hand edit of a generated file. Answering the default
    // would be the one answer this file must never give.
    refuse(plugin = plugin, attribute = attribute, what = "is an empty list, and an empty field is left out")
  }
  return value.items.map { it.name(plugin = plugin, attribute = attribute) }
}

/** One residue attribute as a dict from a name to its own name list, or an empty map when the call states none. */
private fun Map<String, StarlarkValue>.nameListDict(plugin: String, attribute: String): Map<String, List<String>> {
  val value = get(attribute) ?: return emptyMap()
  if (value !is StarlarkDict) {
    refuse(plugin = plugin, attribute = attribute, what = "is not a dict")
  }
  if (value.entries.isEmpty()) {
    refuse(plugin = plugin, attribute = attribute, what = "is an empty dict, and an empty field is left out")
  }
  val result = LinkedHashMap<String, List<String>>()
  for ((key, names) in value.entries) {
    if (names !is StarlarkList) {
      refuse(plugin = plugin, attribute = attribute, what = "states a value that is not a list")
    }
    // An empty list is a real row here, unlike an empty attribute: a jar that merges no library states exactly that.
    val row = names.items.map { it.name(plugin = plugin, attribute = attribute) }
    if (result.put(key.name(plugin = plugin, attribute = attribute), row) != null) {
      refuse(plugin = plugin, attribute = attribute, what = "states one key twice")
    }
  }
  return result
}

private fun StarlarkValue.name(plugin: String, attribute: String): String {
  if (this !is StarlarkText) {
    refuse(plugin = plugin, attribute = attribute, what = "holds a value that is not a string")
  }
  return value
}

private fun refuse(plugin: String, attribute: String, what: String): Nothing = error("$plugin: `$attribute` $what")
