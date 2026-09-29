// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devDistGenerator

import org.jetbrains.intellij.build.forEachConcurrent
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/** The file name of a Bazel package. */
internal const val BUILD_FILE_NAME: String = "BUILD.bazel"

/** The prefix of the marker a person puts into a `BUILD.bazel` to keep a generated section out. */
private const val SKIP_SECTION_MARKER_PREFIX: String = "### skip generation section "

private const val SECTION_TOKEN_PREFIX: String = "### auto-generated section `"

/** The section name prefix the tool owns. The module name follows it. */
internal const val DEV_SECTION_PREFIX: String = "dev "

/**
 * The section names the converter writes. The tool keeps its own sections before the first of them.
 *
 * The converter removes its sections, trims the rest and appends its sections again. A tool section before the first
 * converter section keeps its place through that cycle, so the two writers reach a fixed point.
 */
private val CONVERTER_SECTION_PREFIXES: List<String> = listOf("build ", "iml ", "test ", "maven libs of ")

/** The one load line the converter leaves where it stands. */
private const val PINNED_LOAD_LINE: String = "load(\"//build:dev_server_run_configurations.bzl\", \"dev_server_run_configurations\")"

/**
 * Edits the `dev <module>` sections of one `BUILD.bazel` text in memory.
 *
 * [setSection] and [removeSection] change the sections. [result] then applies the converter's load-line rule and its
 * save form, so the text is the one the converter writes for the same sections.
 */
class DevDistBuildFileEditor(
  /** The text as read from disk. */
  @JvmField val original: String,
) {
  private var content: String = original

  /** Whether the file carries the marker that keeps the generated section [sectionName] out. */
  fun isSectionSkipped(sectionName: String): Boolean = original.contains("$SKIP_SECTION_MARKER_PREFIX`$sectionName`")

  /**
   * States [body] as the section [sectionName].
   *
   * An existing section is replaced in place. A new section goes before the first converter section, or at the end
   * of the file when the file has none. One blank line separates the section from its neighbours.
   */
  fun setSection(sectionName: String, body: String) {
    val startToken = startToken(sectionName)
    val endToken = endToken(sectionName)
    val section = startToken + "\n" + body.trim() + "\n" + endToken
    val startIndex = content.indexOf(startToken)
    val endIndex = if (startIndex == -1) -1 else content.indexOf(endToken, startIndex)
    if (endIndex != -1) {
      content = content.substring(0, startIndex) + section + content.substring(endIndex + endToken.length)
      return
    }
    val insertAt = firstConverterSectionIndex()
    content = if (insertAt == -1) {
      joinBlocks(content, section)
    }
    else {
      joinBlocks(content.substring(0, insertAt), section) + "\n\n" + content.substring(insertAt)
    }
  }

  /** Removes the section [sectionName]. The blank lines around it collapse to one. */
  fun removeSection(sectionName: String) {
    val startToken = startToken(sectionName)
    val endToken = endToken(sectionName)
    val startIndex = content.indexOf(startToken)
    val endIndex = if (startIndex == -1) -1 else content.indexOf(endToken, startIndex)
    if (endIndex == -1) {
      return
    }
    val before = content.substring(0, startIndex).trimEnd('\n')
    val after = content.substring(endIndex + endToken.length).trimStart('\n')
    content = joinBlocks(before, after)
  }

  /**
   * The text to save, with the load lines the sections need merged in.
   *
   * This is the converter's `appendLoadSymbols` and `save`. Every `load(` line of the file moves to the head, except
   * the pinned one. [loadSymbols] adds the symbols the tool's sections need, keyed by `.bzl` file. A symbol the
   * sections take from one `.bzl` file leaves the load line of every other file. A symbol no line outside the load
   * lines names is dropped. The files sort in the load-statement order of [BazelLabelComparator]. The text ends with
   * one newline.
   */
  fun result(loadSymbols: Map<String, Set<String>>): String {
    val manager = BazelLoadStatementManager()
    manager.insert(loadSymbols)
    for (line in original.lines()) {
      if (isLoadLineToMove(line)) {
        manager.insert(line)
      }
    }
    manager.keepOneOrigin(loadSymbols)
    val stripped = content.lines().filterNot { isLoadLineToMove(it) }.joinToString(separator = "\n").trim()
    val identifiers = identifiersOf(stripped)
    manager.retainUsedSymbols { identifiers.contains(it) }
    val loads = manager.getResult()
    val text = if (loads.isEmpty()) stripped else loads + "\n\n" + stripped
    return text.trim() + "\n"
  }

  /** The module names of every section whose name starts with [prefix], in file order. */
  fun sectionModules(prefix: String): List<String> {
    val result = ArrayList<String>()
    var from = 0
    val token = "$SECTION_TOKEN_PREFIX$prefix"
    while (true) {
      val index = content.indexOf(token, from)
      if (index == -1) {
        return result
      }
      val nameEnd = content.indexOf('`', index + token.length)
      if (nameEnd != -1 && content.startsWith("` start", nameEnd)) {
        result.add(content.substring(index + token.length, nameEnd))
      }
      from = index + token.length
    }
  }

  /** The index of the line that starts the first converter section, or `-1`. */
  private fun firstConverterSectionIndex(): Int {
    var from = 0
    while (true) {
      val index = content.indexOf(SECTION_TOKEN_PREFIX, from)
      if (index == -1) {
        return -1
      }
      val atLineStart = index == 0 || content[index - 1] == '\n'
      if (atLineStart && CONVERTER_SECTION_PREFIXES.any { content.startsWith(it, index + SECTION_TOKEN_PREFIX.length) }) {
        return index
      }
      from = index + SECTION_TOKEN_PREFIX.length
    }
  }
}

private fun startToken(sectionName: String): String = "$SECTION_TOKEN_PREFIX$sectionName` start"

private fun endToken(sectionName: String): String = "$SECTION_TOKEN_PREFIX$sectionName` end"

/** Joins two blocks with one blank line. An empty block adds nothing. */
private fun joinBlocks(first: String, second: String): String {
  val head = first.trimEnd()
  val tail = second.trimStart()
  return when {
    head.isEmpty() -> tail
    tail.isEmpty() -> head
    else -> head + "\n\n" + tail
  }
}

private fun isLoadLineToMove(line: String): Boolean = line.startsWith("load(") && line != PINNED_LOAD_LINE

/**
 * The `BUILD.bazel` texts of one run, each read once.
 *
 * The content-module-jar gate reads the `build` skip marker, and the section writer edits the same text. Both go
 * through this cache, so a file is read one time. A package without a `BUILD.bazel` has no editor.
 */
internal class DevDistBuildFiles(private val index: DevDistBazelIndex) {
  private val editors = ConcurrentHashMap<Path, Optional<DevDistBuildFileEditor>>()

  /** The editor of [file], or `null` when the file does not exist. Two readers of one file get the same editor. */
  fun editor(file: Path): DevDistBuildFileEditor? {
    editors.get(file)?.let { return it.orElse(null) }
    val editor = Optional.ofNullable(if (Files.isRegularFile(file)) DevDistBuildFileEditor(Files.readString(file)) else null)
    return (editors.putIfAbsent(file, editor) ?: editor).orElse(null)
  }

  /** Reads [files] concurrently, so that a later loop over them reads no file. */
  fun preload(files: Collection<Path>) {
    files.forEachConcurrent { editor(it) }
  }

  /** The `BUILD.bazel` of the package of [module], or `null` when the JSON does not place the module. */
  fun buildFile(module: String): Path? = index.packageDir(module)?.resolve(BUILD_FILE_NAME)

  /** Whether a person took over the `build` section of [module], which drops its `content_module_jar` call. */
  fun isBuildSectionSkipped(module: String): Boolean {
    val file = buildFile(module) ?: return false
    return editor(file)?.isSectionSkipped("build $module") == true
  }
}
