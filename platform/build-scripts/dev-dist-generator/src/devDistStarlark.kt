// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.annotations.ApiStatus

/**
 * Starlark rendering primitives.
 *
 * The tool writes the same bytes as the converter. So it keeps every converter rule: the indent, the one-line form,
 * the symbol sort and the load-file order. It ports the converter's `dsl.kt`, `BazelLoadStatementManager.kt`,
 * `BazelLabelComparator.kt` and the `identifiersOf` scan of `BazelFileUpdater.kt`.
 */

internal const val INDENT = "    "

/** [value] as a Starlark string literal. A backslash and a double quote get an escape; a control character is refused. */
@ApiStatus.Internal
fun quoteStarlarkString(value: String): String {
  require(value.none { it.isISOControl() }) { "A Starlark value contains a control character" }
  return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

/** A value that renders its own Starlark text. */
@ApiStatus.Internal
interface Renderable {
  fun render(): String
}

/** Rendered as `# do not sort` at the head of a list whose order carries meaning. */
internal object DoNotSortIndicator

/** Prepends [DoNotSortIndicator] when the list has two or more items. */
internal fun List<Any>.unsorted(): List<Any> {
  return if (this.size < 2) {
    this
  }
  else {
    listOf<Any>(DoNotSortIndicator) + this
  }
}

/** A string list nested one level deep, inside a dict attribute value. It renders the way buildifier leaves it. */
internal class NestedStringList(private val values: List<String>) : Renderable {
  override fun render(): String {
    if (values.size <= 1) {
      return values.joinToString(", ", "[", "]") { "\"$it\"" }
    }
    val itemIndent = INDENT.repeat(3)
    return values.joinToString(",\n$itemIndent", "[\n$itemIndent", ",\n$INDENT$INDENT]") { "\"$it\"" }
  }
}

/**
 * A `glob([...])` call, in the form the converter renders and buildifier keeps: one pattern inline, several one per
 * line, and no `allow_empty`, so an empty match fails the load. The [exclude] list renders in the same form.
 */
internal class GlobCall(private val patterns: List<String>, private val exclude: List<String> = emptyList()) : Renderable {
  override fun render(): String {
    require(patterns.isNotEmpty()) { "A glob needs a pattern" }
    return buildString {
      appendLine("glob(")
      appendList(prefix = "", values = patterns)
      if (exclude.isNotEmpty()) {
        appendList(prefix = "exclude = ", values = exclude)
      }
      append("$INDENT)")
    }
  }

  private fun StringBuilder.appendList(prefix: String, values: List<String>) {
    if (values.size == 1) {
      appendLine("$INDENT$INDENT$prefix[${quoteStarlarkString(values.single())}],")
      return
    }
    appendLine("$INDENT$INDENT$prefix[")
    for (value in values) {
      appendLine("$INDENT$INDENT$INDENT${quoteStarlarkString(value)},")
    }
    appendLine("$INDENT$INDENT],")
  }
}

/** One `load(...)` line. The symbols render sorted. The section writer merges these lines into the file head. */
@ApiStatus.Internal
data class LoadStatement(@JvmField val bzlFile: String, @JvmField val symbols: List<String>) : Renderable {
  override fun render(): String {
    val formattedSymbols = symbols.sorted().joinToString(", ") { "\"$it\"" }
    return """load("$bzlFile", $formattedSymbols)"""
  }
}

/**
 * One rule call. The attributes render in the order [option] received them.
 *
 * A call with one scalar attribute renders on one line, as buildifier leaves it. The other calls render one
 * attribute per line.
 */
@ApiStatus.Internal
class Target(private val type: String) : Renderable {
  private val attributes = LinkedHashMap<String, Any>()

  fun option(key: String, value: Any) {
    verifyTypeIsSupported(value)

    if (attributes.containsKey(key)) {
      error("Duplicate key: $key. Old value: ${attributes.get(key)}, new value: $value")
    }

    attributes.put(key, value)
  }

  private fun verifyTypeIsSupported(value: Any) {
    val klass = value.javaClass
    when {
      String::class.java.isAssignableFrom(klass) -> Unit
      value == true || value == false -> Unit
      Renderable::class.java.isAssignableFrom(klass) -> Unit
      List::class.java.isAssignableFrom(klass) -> {
        for (item in value as List<*>) {
          verifyTypeIsSupported(item!!)
        }
      }
      LinkedHashSet::class.java.isAssignableFrom(klass) -> {
        for (item in value as LinkedHashSet<*>) {
          verifyTypeIsSupported(item!!)
        }
      }
      HashMap::class.java.isAssignableFrom(klass) -> {
        for (item in value as Map<*, *>) {
          verifyTypeIsSupported(item.key!!)
          verifyTypeIsSupported(item.value!!)
        }
      }
      DoNotSortIndicator::class.java.isAssignableFrom(klass) -> Unit
      else -> error("Unsupported type '$klass' for value: $value")
    }
  }

  fun visibility(targets: Array<String>) {
    option("visibility", targets.toList())
  }

  override fun render(): String {
    val renderedAttributes = attributes.map { (key, value) ->
      "$INDENT$key = ${formatValue(value)},"
    }.joinToString("\n")

    return buildString {
      if (type.isEmpty()) {
        appendLine(renderedAttributes)
      }
      else if (attributes.size == 1 && !renderedAttributes.contains('\n')) {
        appendLine("$type(${renderedAttributes.trim().removeSuffix(",")})")
      }
      else {
        appendLine("$type(")
        appendLine(renderedAttributes)
        appendLine(")")
      }
    }
  }
}

/**
 * Renders one attribute value.
 *
 * A collection of one item or less renders inline. A collection of two or more items renders one item per line.
 * A map always renders one entry per line. A string renders as [quoteStarlarkString] writes it.
 */
internal fun formatValue(value: Any?): String {
  return when (value) {
    is Collection<*> -> {
      if (value.size > 1) {
        value.joinToString(separator = ",\n$INDENT$INDENT", prefix = "[\n$INDENT$INDENT", postfix = ",\n$INDENT]") { formatValue(it) }
      }
      else {
        value.joinToString(separator = ", ", prefix = "[", postfix = "]") { formatValue(it) }
      }
    }
    is Array<*> -> value.joinToString(separator = ", ", prefix = "[", postfix = "]") { formatValue(it) }
    is String -> quoteStarlarkString(value)
    is Number -> value.toString()
    is Map<*, *> -> value.entries.joinToString(",\n$INDENT$INDENT", prefix = "{\n$INDENT$INDENT", postfix = ",\n$INDENT}") {
      (key, value) -> "${quoteStarlarkString(key.toString())}: ${formatValue(value)}"
    }
    true -> "True"
    false -> "False"
    is Renderable -> value.render()
    is DoNotSortIndicator -> "# do not sort"
    else -> value.toString()
  }
}

/**
 * Orders Bazel labels.
 *
 * The group of a label comes first: `@` repository, then `//` absolute, then `:` local, then the rest.
 * [forLoadStatements] reverses the group order. Inside a group the parts split on `.` and `:` compare one
 * by one, and the shorter label wins when the shared parts are equal. [BazelLoadStatementManager] orders the load
 * files with it.
 */
internal class BazelLabelComparator(
  @JvmField val forLoadStatements: Boolean = false,
) : Comparator<String> {

  override fun compare(a: String, b: String): Int {
    return groupComparator
      .then(labelPartsComparator)
      .thenBy { it }
      .compare(a, b)
  }

  private val groupComparator = Comparator<String> { a, b ->
    val direction = if (forLoadStatements) -1 else 1
    getGroup(a).compareTo(getGroup(b)) * direction
  }

  private fun getGroup(s: String) = when {
    s.startsWith("@") -> 3
    s.startsWith("//") -> 2
    s.startsWith(":") -> 1
    else -> 0
  }

  private val labelPartsComparator = Comparator<String> { a, b ->
    val aLabelParts = labelParts(a)
    val bLabelParts = labelParts(b)

    for (i in 0 until minOf(aLabelParts.size, bLabelParts.size)) {
      val compareResult = aLabelParts[i].compareTo(bLabelParts[i])
      if (compareResult != 0) return@Comparator compareResult
    }

    aLabelParts.size.compareTo(bLabelParts.size)
  }

  private fun labelParts(s: String): List<String> {
    return s.replace(':', '.')
      .split('.')
  }
}

/**
 * Merges the `load(...)` lines of one `BUILD.bazel` file.
 *
 * A symbol entry is the quoted token, `"symbol"` or `alias = "symbol"`. The entries of one file are sorted and
 * distinct. [getResult] orders the files with [BazelLabelComparator] in the load-statement direction. The section
 * writer merges the load statements of a `BUILD.bazel` file with it.
 */
class BazelLoadStatementManager {
  private val comparator = BazelLabelComparator(forLoadStatements = true)
  private val loadStatements = mutableMapOf<String, List<String>>()

  fun insert(entries: Map<String, Set<String>>) {
    entries.entries.forEach { entry -> insert(entry.key, entry.value.toList().map { "\"$it\"" }) }
  }

  fun insert(line: String) {
    val matches = line.removePrefix("load(")
      .removeSuffix(")")
      .split(", ")
    val extension = matches.first()
      .removeSurrounding("\"")
    val symbols = matches.drop(1)
    insert(extension, symbols)
  }

  private fun insert(extension: String, symbols: List<String>) {
    loadStatements.compute(extension) { _, value ->
      (value.orEmpty() + symbols).sorted().distinct()
    }
  }

  /**
   * Removes from every other `.bzl` file the entries that bind a name [entries] binds.
   *
   * A `BUILD.bazel` file binds a name once. When a section takes a symbol from another `.bzl` file than before, the
   * load line of the old origin has to go, or the file binds the name twice and does not load.
   */
  fun keepOneOrigin(entries: Map<String, Set<String>>) {
    for ((extension, names) in entries) {
      for (other in loadStatements.keys.toList()) {
        if (other == extension) {
          continue
        }
        val kept = loadStatements.getValue(other).filterNot { boundName(it) in names }
        if (kept.isEmpty()) {
          loadStatements.remove(other)
        }
        else {
          loadStatements.put(other, kept)
        }
      }
    }
  }

  /**
   * Removes each symbol that [isUsed] rejects, and removes an extension that keeps no symbol.
   *
   * [isUsed] receives the name the load statement binds. For `alias = "original"` that name is `alias`.
   */
  fun retainUsedSymbols(isUsed: (String) -> Boolean) {
    val iterator = loadStatements.entries.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      val kept = entry.value.filter { isUsed(boundName(it)) }
      if (kept.isEmpty()) {
        iterator.remove()
      }
      else if (kept.size != entry.value.size) {
        entry.setValue(kept)
      }
    }
  }

  fun getResult(): String {
    return loadStatements.entries
      .sortedWith { a, b -> comparator.compare(a.key, b.key) }
      .joinToString("\n") { entry ->
        """load("${entry.key}", ${entry.value.joinToString { it }})"""
      }
  }
}

/**
 * The name a load statement entry binds in the BUILD file.
 *
 * An entry is either `"symbol"` or `alias = "symbol"`.
 */
private fun boundName(entry: String): String {
  val alias = entry.substringBefore(delimiter = '=', missingDelimiterValue = "").trim()
  return alias.ifEmpty { entry.trim().removeSurrounding("\"") }
}

/**
 * Every identifier the content names.
 *
 * An identifier is a maximal run of letters, digits and `_`. The scan covers strings and comments too. The section
 * writer feeds it to [BazelLoadStatementManager.retainUsedSymbols].
 */
internal fun identifiersOf(content: String): Set<String> {
  val result = HashSet<String>()
  var start = -1
  for (index in content.indices) {
    val c = content[index]
    if (c == '_' || (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9')) {
      if (start == -1) {
        start = index
      }
    }
    else if (start != -1) {
      result.add(content.substring(start, index))
      start = -1
    }
  }
  if (start != -1) {
    result.add(content.substring(start))
  }
  return result
}
