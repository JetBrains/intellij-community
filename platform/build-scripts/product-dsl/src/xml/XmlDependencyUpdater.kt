// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.xml

import com.intellij.util.xml.dom.createXmlStreamReaderWithLocation
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLStreamConstants

/**
 * Utilities for updating XML dependency sections in plugin.xml and module descriptor files.
 * Preserves formatting outside `<dependencies>` section.
 */

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
 * Updates XML dependencies in a file, preserving manual dependencies.
 *
 * @param path Path to the file to update
 * @param content Current file content
 * @param moduleDependencies New module dependencies to add
 * @param preserveExistingModule Predicate to identify which existing modules should be preserved (manual deps)
 * @return FileChangeStatus indicating what changed
 */
internal fun updateXmlDependencies(
  path: Path,
  content: String,
  moduleDependencies: List<String>,
  preserveExistingModule: ((String) -> Boolean)? = null,
): FileChangeStatus {
  if (content.isEmpty()) {
    return FileChangeStatus.UNCHANGED
  }

  val info = parseDependenciesInfo(content)
  if (info == null) {
    if (moduleDependencies.isEmpty()) {
      return FileChangeStatus.UNCHANGED
    }
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