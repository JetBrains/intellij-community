// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout

import com.intellij.util.xml.dom.createXmlStreamReaderWithLocation
import org.jetbrains.intellij.build.productLayout.analysis.ModuleSetTraversal
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLStreamConstants

/**
 * Shared utilities for generating plugin.xml content for both products and module sets.
 * Both products and module sets are "content containers" that:
 * - Contain nested module sets
 * - Declare module aliases
 * - Generate content blocks with modules
 */

/**
 * Visits all module sets recursively, applying the visitor function to each.
 * More efficient than collecting when you only need iteration (avoids intermediate collection).
 *
 * @param sets List of module sets to visit
 * @param visitor Function to apply to each module set
 */
internal fun visitAllModuleSets(sets: List<ModuleSet>, visitor: (ModuleSet) -> Unit) {
  for (set in sets) {
    visitor(set)
    visitAllModuleSets(set.nestedSets, visitor)
  }
}

/**
 * Recursively collects all aliases from a module set and its nested sets.
 * Collects the single `alias` field (for module set's own capability).
 *
 * @param moduleSet The module set to collect aliases from
 * @param result Accumulator for collecting aliases (avoids intermediate allocations)
 * @return List of all aliases found in the module set hierarchy
 */
internal fun collectAllAliases(
  moduleSet: ModuleSet,
  result: MutableList<String> = mutableListOf()
): List<String> {
  if (moduleSet.alias != null) {
    result.add(moduleSet.alias)
  }
  moduleSet.nestedSets.forEach { collectAllAliases(it, result) }
  return result
}

/**
 * Builds multiple `<module value="..."/>` declarations from a list of aliases.
 *
 * @param aliases The list of module aliases (e.g., ["com.intellij.modules.idea", "com.intellij.modules.java-capable"])
 * @return XML string with all aliases, or empty string if list is empty
 */
internal fun buildModuleAliasesXml(aliases: List<String>): String {
  return aliases.joinToString("") { "  <module value=\"$it\"/>\n" }
}

/**
 * Wraps content with editor-fold comments for collapsible sections in IDE.
 * Uses try-finally to ensure the closing tag is always written, even if block throws.
 *
 * @param sb StringBuilder to append to
 * @param indent Indentation string (e.g., "    ")
 * @param description Description for the fold (shown in IDE)
 * @param block Lambda that generates the content between fold tags
 */
internal inline fun withEditorFold(sb: StringBuilder, indent: String, description: String, block: () -> Unit) {
  sb.append("$indent<!-- <editor-fold desc=\"$description\"> -->\n")
  try {
    block()
  }
  finally {
    sb.append("$indent<!-- </editor-fold> -->\n")
  }
}

/**
 * Visits all modules recursively (including nested sets), applying the visitor function to each.
 * More efficient than collecting when you only need iteration (avoids intermediate collection).
 *
 * @param moduleSet The module set to visit
 * @param visitor Function to apply to each module
 */
internal fun visitAllModules(moduleSet: ModuleSet, visitor: (ContentModule) -> Unit) {
  for (module in moduleSet.modules) {
    visitor(module)
  }
  for (nestedSet in moduleSet.nestedSets) {
    visitAllModules(nestedSet, visitor)
  }
}

/**
 * Finds a module set by name and collects all module names from it and its nested sets.
 * Delegates to [ModuleSetTraversal.collectAllModuleNames] for the actual traversal.
 *
 * @param moduleSets List of all module sets to search in
 * @param setName Name of the module set to start collecting from
 * @return Set of all module names found in the module set hierarchy, or empty set if not found
 */
internal fun collectAllModuleNamesFromSet(
  moduleSets: List<ModuleSet>,
  setName: String,
): Set<String> {
  val moduleSet = moduleSets.firstOrNull { it.name == setName } ?: return emptySet()
  return ModuleSetTraversal.collectAllModuleNames(moduleSet)
}

/**
 * Checks if a module set contains (directly or transitively) any nested set whose name is in the given set.
 * Used to detect when a parent module set contains overridden nested sets.
 */
internal fun containsOverriddenNestedSet(moduleSet: ModuleSet, overriddenNames: Set<ModuleSetName>): Boolean {
  // Check direct nested sets
  for (nestedSet in moduleSet.nestedSets) {
    if (ModuleSetName(nestedSet.name) in overriddenNames) {
      return true
    }
    // Check recursively
    if (containsOverriddenNestedSet(nestedSet, overriddenNames)) {
      return true
    }
  }
  return false
}

/**
 * Finds all nested set names (directly or transitively) that are in the given set of names.
 * Used for generating descriptive comments about which nested sets are overridden.
 */
internal fun findOverriddenNestedSetNames(moduleSet: ModuleSet, overriddenNames: Set<ModuleSetName>): List<ModuleSetName> {
  val result = mutableListOf<ModuleSetName>()
  for (nestedSet in moduleSet.nestedSets) {
    val nestedSetName = ModuleSetName(nestedSet.name)
    if (nestedSetName in overriddenNames) {
      result.add(nestedSetName)
    }
    // Check recursively
    result.addAll(findOverriddenNestedSetNames(nestedSet, overriddenNames))
  }
  return result
}

// =====================================================================================================================
// XML Dependency Utilities (preserves formatting outside <dependencies> section)
// =====================================================================================================================

private const val EDITOR_FOLD_START = "<!-- editor-fold desc=\"Generated dependencies - do not edit manually\" -->"
private const val EDITOR_FOLD_END = "<!-- end editor-fold -->"
private const val EDITOR_FOLD_MARKER = "editor-fold desc=\"Generated dependencies"

private enum class EditorFoldType { NONE, WRAPS_ENTIRE_SECTION, INSIDE_SECTION }

private sealed class DepEntry {
  data class Plugin(val id: String) : DepEntry()
  data class Module(val name: String) : DepEntry()
}

private class DependenciesInfo(
  @JvmField val startOffset: Int,
  @JvmField val endOffset: Int,
  @JvmField val entries: List<DepEntry>,  // All entries in original order
  @JvmField val indent: String,
  @JvmField val editorFoldType: EditorFoldType,
)

private class EditorFoldRegion(
  @JvmField val startOffset: Int,
  @JvmField val endOffset: Int,
  @JvmField val indent: String,
)

private fun findEditorFoldRegion(content: String, markerStart: Int): EditorFoldRegion? {
  val endIndex = content.indexOf(EDITOR_FOLD_END, markerStart)
  if (endIndex == -1) return null

  val lineStart = content.lastIndexOf('\n', markerStart).let { if (it == -1) 0 else it + 1 }
  val indent = content.substring(lineStart, markerStart).takeWhile { it == ' ' || it == '\t' }
  var endOffset = endIndex + EDITOR_FOLD_END.length
  if (endOffset < content.length && content[endOffset] == '\n') endOffset++

  return EditorFoldRegion(lineStart, endOffset, indent)
}

private fun StringBuilder.appendModules(indent: String, modules: List<String>) {
  for (m in modules) append(indent).append("<module name=\"").append(m).append("\"/>\n")
}

/**
 * @param preserveExistingModule predicate to identify which existing modules should be preserved (manual deps)
 */
internal fun updateXmlDependencies(
  path: Path,
  moduleDependencies: List<String>,
  preserveExistingModule: ((String) -> Boolean)? = null,
): FileChangeStatus {
  if (Files.notExists(path)) return FileChangeStatus.UNCHANGED
  return updateXmlDependencies(path = path, content = Files.readString(path), moduleDependencies = moduleDependencies, preserveExistingModule = preserveExistingModule)
}

/**
 * @param preserveExistingModule predicate to identify which existing modules should be preserved (manual deps)
 */
internal fun updateXmlDependencies(
  path: Path,
  content: String,
  moduleDependencies: List<String>,
  preserveExistingModule: ((String) -> Boolean)? = null,
): FileChangeStatus {
  if (content.isEmpty()) return FileChangeStatus.UNCHANGED

  val info = parseDependenciesInfo(content)
  if (info == null) {
    if (moduleDependencies.isEmpty()) return FileChangeStatus.UNCHANGED
    return writeIfChanged(path = path, oldContent = content, newContent = insertDependenciesSection(content, moduleDependencies))
  }

  // Extract module names for convenience
  val moduleNames = info.entries.filterIsInstance<DepEntry.Module>().map { it.name }

  // Manual entries = plugins + manual modules (in original order)
  val manualEntries = info.entries.filter { entry ->
    when (entry) {
      is DepEntry.Plugin -> true
      is DepEntry.Module -> preserveExistingModule?.invoke(entry.name) == true
    }
  }
  val manualModuleNames = manualEntries.filterIsInstance<DepEntry.Module>().map { it.name }

  val autoModules = moduleDependencies.sorted()

  // Skip writing if the module set is unchanged (avoid formatting-only changes like adding editor-fold markers)
  if (moduleNames.sorted() == (autoModules + manualModuleNames).distinct().sorted()) {
    return FileChangeStatus.UNCHANGED
  }

  val replacement = when {
    autoModules.isEmpty() && manualEntries.isEmpty() -> ""
    else -> when (info.editorFoldType) {
      EditorFoldType.WRAPS_ENTIRE_SECTION -> buildFullBlock(info.indent, manualEntries, autoModules)
      EditorFoldType.INSIDE_SECTION -> buildFoldOnly(info.indent, autoModules)  // No manual deps - they're in prefix
      EditorFoldType.NONE -> buildWithEntries(info.indent, manualEntries, autoModules)
    }
  }

  return writeIfChanged(path = path, oldContent = content, newContent = content.substring(0, info.startOffset) + replacement + content.substring(info.endOffset))
}

private fun parseDependenciesInfo(content: String): DependenciesInfo? {
  // Find <dependencies> section using StAX
  var depsStart = -1
  var depsLineStart = -1
  var depsEnd = -1
  var depsIndent = ""
  val entries = mutableListOf<DepEntry>()

  val reader = createXmlStreamReaderWithLocation(StringReader(content))
  try {
    var depth = 0
    while (reader.hasNext()) {
      when (reader.next()) {
        XMLStreamConstants.START_ELEMENT -> {
          if (reader.localName == "dependencies" && depsStart == -1) {
            depsStart = content.lastIndexOf("<dependencies", reader.location.characterOffset)
            depsLineStart = content.lastIndexOf('\n', depsStart).let { if (it == -1) 0 else it + 1 }
            depsIndent = content.substring(depsLineStart, depsStart).takeWhile { it == ' ' || it == '\t' }
            depth = 1
          }
          else if (depth > 0) {
            depth++
            when (reader.localName) {
              "plugin" -> reader.getAttributeValue(null, "id")?.let { entries.add(DepEntry.Plugin(it)) }
              "module" -> reader.getAttributeValue(null, "name")?.let { entries.add(DepEntry.Module(it)) }
            }
          }
        }
        XMLStreamConstants.END_ELEMENT -> if (depth > 0) {
          depth--
          if (depth == 0) {
            depsEnd = content.indexOf('>', reader.location.characterOffset) + 1
            if (depsEnd < content.length && content[depsEnd] == '\n') depsEnd++
            break
          }
        }
      }
    }
  }
  finally {
    reader.close()
  }

  if (depsStart == -1) return null

  // Check editor-fold position relative to <dependencies>
  val foldStart = content.indexOf(EDITOR_FOLD_MARKER)
  if (foldStart == -1) {
    return DependenciesInfo(depsLineStart, depsEnd, entries, depsIndent, EditorFoldType.NONE)
  }

  val fold = findEditorFoldRegion(content, foldStart)
    ?: return DependenciesInfo(depsLineStart, depsEnd, entries, depsIndent, EditorFoldType.NONE)

  return if (foldStart < depsStart) {
    // Editor-fold wraps entire section (module descriptors)
    DependenciesInfo(fold.startOffset, fold.endOffset, entries, fold.indent, EditorFoldType.WRAPS_ENTIRE_SECTION)
  }
  else {
    // Editor-fold inside section (plugin.xml)
    DependenciesInfo(fold.startOffset, fold.endOffset, entries, fold.indent, EditorFoldType.INSIDE_SECTION)
  }
}

private fun insertDependenciesSection(content: String, modules: List<String>): String {
  val pos = content.indexOf('>', content.indexOf("<idea-plugin"))
  if (pos == -1) return content

  val nextLine = content.indexOf('\n', pos + 1)
  val indent = (if (nextLine != -1) content.substring(nextLine + 1).takeWhile { it == ' ' || it == '\t' } else "").ifEmpty { "  " }
  val suffix = content.substring(pos + 1).let { if (it.startsWith('\n')) it.substring(1) else it }
  return content.substring(0, pos + 1) + "\n" + buildFullBlock(indent, emptyList(), modules) + suffix
}

/** Editor-fold only with auto-generated modules (for INSIDE_SECTION - replacing fold content) */
private fun buildFoldOnly(indent: String, autoModules: List<String>): String {
  if (autoModules.isEmpty()) return ""
  return StringBuilder().apply {
    append(indent).append(EDITOR_FOLD_START).append("\n")
    appendModules(indent, autoModules)
    append(indent).append(EDITOR_FOLD_END).append("\n")
  }.toString()
}

/** Full editor-fold block with <dependencies> (for WRAPS_ENTIRE_SECTION - module descriptors) */
private fun buildFullBlock(indent: String, manualEntries: List<DepEntry>, autoModules: List<String>): String {
  val manualModules = manualEntries.filterIsInstance<DepEntry.Module>().map { it.name }
  return StringBuilder().apply {
    append(indent).append(EDITOR_FOLD_START).append("\n")
    append(indent).append("<dependencies>\n")
    appendModules("$indent  ", manualModules)
    appendModules("$indent  ", autoModules)
    append(indent).append("</dependencies>\n")
    append(indent).append(EDITOR_FOLD_END).append("\n")
  }.toString()
}

/** Full <dependencies> section preserving original order of plugins + manual modules (for NONE case) */
private fun buildWithEntries(indent: String, manualEntries: List<DepEntry>, autoModules: List<String>): String {
  return StringBuilder().apply {
    append(indent).append("<dependencies>\n")
    for (entry in manualEntries) {
      when (entry) {
        is DepEntry.Plugin -> append(indent).append("  <plugin id=\"").append(entry.id).append("\"/>\n")
        is DepEntry.Module -> append(indent).append("  <module name=\"").append(entry.name).append("\"/>\n")
      }
    }
    if (autoModules.isNotEmpty()) {
      append(indent).append("  ").append(EDITOR_FOLD_START).append("\n")
      appendModules("$indent  ", autoModules)
      append(indent).append("  ").append(EDITOR_FOLD_END).append("\n")
    }
    append(indent).append("</dependencies>\n")
  }.toString()
}

private fun writeIfChanged(path: Path, oldContent: String, newContent: String): FileChangeStatus {
  if (newContent == oldContent) return FileChangeStatus.UNCHANGED
  Files.writeString(path, newContent)
  return if (oldContent.contains(EDITOR_FOLD_MARKER)) FileChangeStatus.MODIFIED else FileChangeStatus.CREATED
}

/**
 * Extracts content module names from plugin.xml text content.
 * Uses StAX for robust parsing with proper element nesting handling.
 *
 * Returns `null` if any module contains '/' (plugin should be skipped entirely from dependency generation).
 */
internal fun extractContentModulesFromText(content: String): Set<String>? {
  val result = LinkedHashSet<String>()
  val reader = createXmlStreamReaderWithLocation(StringReader(content))
  try {
    var inContent = false
    var depth = 0
    while (reader.hasNext()) {
      when (reader.next()) {
        XMLStreamConstants.START_ELEMENT -> {
          val localName = reader.localName
          if (localName == "content" && !inContent) {
            inContent = true
            depth = 1
          }
          else if (inContent) {
            depth++
            if (localName == "module") {
              val name = reader.getAttributeValue(null, "name")
              if (name != null) {
                if (name.contains('/')) {
                  return null
                }
                result.add(name)
              }
            }
          }
        }
        XMLStreamConstants.END_ELEMENT -> {
          if (inContent) {
            depth--
            if (depth == 0) {
              break // Exit after </content>
            }
          }
        }
      }
    }
  }
  finally {
    reader.close()
  }
  return result
}