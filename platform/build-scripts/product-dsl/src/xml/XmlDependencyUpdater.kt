// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.xml

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.util.xml.dom.createXmlStreamReaderWithLocation
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.util.FileUpdateStrategy
import java.io.StringReader
import java.nio.file.Path
import javax.xml.stream.XMLStreamConstants

/**
 * Utilities for updating XML dependency sections in plugin.xml and module descriptor files.
 * Preserves formatting outside `<dependencies>` section.
 */

private const val REGION_START = "<!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->"
private const val REGION_END = "<!-- endregion -->"
private const val REGION_MARKER = "region Generated dependencies"
// Legacy marker for backward compatibility with existing files
private const val LEGACY_MARKER = "editor-fold desc=\"Generated dependencies"

private enum class RegionType { NONE, WRAPS_ENTIRE_SECTION, INSIDE_SECTION }

private sealed class DepEntry {
  data class Plugin(val id: String) : DepEntry()
  data class Module(val name: String) : DepEntry()
}

internal data class ParsedDependenciesEntries(
  @JvmField val moduleNames: List<String>,
  @JvmField val pluginIds: List<String>,
)

private class DependenciesInfo(
  @JvmField val startOffset: Int,
  @JvmField val endOffset: Int,
  @JvmField val entries: List<DepEntry>,  // All entries in original order
  @JvmField val entriesInRegion: Set<DepEntry>,  // Entries inside generated region only
  @JvmField val indent: String,
  @JvmField val regionType: RegionType,
)

private class FoldRegion(
  @JvmField val startOffset: Int,
  @JvmField val endOffset: Int,
  @JvmField val indent: String,
)

private fun findFoldRegion(content: String, markerStart: Int): FoldRegion? {
  // Try new region end marker first, then legacy
  var endIndex = content.indexOf(REGION_END, markerStart)
  var endMarkerLength = REGION_END.length
  if (endIndex == -1) {
    endIndex = content.indexOf("<!-- end editor-fold -->", markerStart)
    endMarkerLength = "<!-- end editor-fold -->".length
  }
  if (endIndex == -1) return null

  val lineStart = content.lastIndexOf('\n', markerStart).let { if (it == -1) 0 else it + 1 }
  val indent = content.substring(lineStart, markerStart).takeWhile { it == ' ' || it == '\t' }
  var endOffset = endIndex + endMarkerLength
  if (endOffset < content.length && content[endOffset] == '\n') endOffset++

  return FoldRegion(lineStart, endOffset, indent)
}

private fun StringBuilder.appendModules(indent: String, modules: List<String>) {
  for (m in modules) append(indent).append("<module name=\"").append(m).append("\"/>\n")
}

private fun StringBuilder.appendPlugins(indent: String, plugins: List<String>) {
  for (p in plugins) append(indent).append("<plugin id=\"").append(p).append("\"/>\n")
}

/**
 * Updates XML dependencies in a file, preserving manual dependencies.
 *
 * @param path Path to the file to update
 * @param content Current file content
 * @param moduleDependencies New module dependencies to add (`<module name="..."/>`)
 * @param pluginDependencies New plugin dependencies to add (`<plugin id="..."/>`)
 * @param preserveExistingModule Predicate to identify which existing modules should be preserved (manual deps)
 * @param preserveExistingPlugin Predicate to identify which existing plugins should be preserved (manual deps)
 * @param xiIncludeModuleDeps Module deps already present via xi:includes (not to be duplicated in main file)
 * @param xiIncludePluginDeps Plugin deps already present via xi:includes (not to be duplicated in main file)
 * @param strategy File update strategy (actual writer or dry run recorder)
 * @return FileChangeStatus indicating what changed
 */
internal fun updateXmlDependencies(
  path: Path,
  content: String,
  moduleDependencies: List<String>,
  pluginDependencies: List<String> = emptyList(),
  preserveExistingModule: ((String) -> Boolean)? = null,
  preserveExistingPlugin: ((String) -> Boolean)? = null,
  legacyPluginDependencies: List<String> = emptyList(),
  xiIncludeModuleDeps: Set<ContentModuleName> = emptySet(),
  xiIncludePluginDeps: Set<PluginId> = emptySet(),
  strategy: FileUpdateStrategy,
): FileChangeStatus {
  if (content.isEmpty()) {
    return FileChangeStatus.UNCHANGED
  }

  val info = parseDependenciesInfo(content)
  if (info == null) {
    if (moduleDependencies.isEmpty() && pluginDependencies.isEmpty()) {
      return FileChangeStatus.UNCHANGED
    }
    // Compare with legacy <depends> - if semantically same, don't convert format
    if (moduleDependencies.isEmpty() && legacyPluginDependencies.isNotEmpty()) {
      if (pluginDependencies.sorted() == legacyPluginDependencies.sorted()) {
        return FileChangeStatus.UNCHANGED
      }
    }
    // Check if all auto-derived deps are covered by xi:includes - if so, no need to insert in main file
    val xiIncludeModuleValues = xiIncludeModuleDeps.mapTo(HashSet()) { it.value }
    val xiIncludePluginValues = xiIncludePluginDeps.mapTo(HashSet()) { it.value }
    val uncoveredModules = moduleDependencies.toSet() - xiIncludeModuleValues
    val uncoveredPlugins = pluginDependencies.toSet() - xiIncludePluginValues
    if (uncoveredModules.isEmpty() && uncoveredPlugins.isEmpty()) {
      return FileChangeStatus.UNCHANGED  // All deps in xi:includes, no modification needed
    }
    // Only insert deps NOT covered by xi:includes
    return strategy.writeIfChanged(path = path, oldContent = content, newContent = insertDependenciesSection(content, uncoveredModules.sorted(), uncoveredPlugins.sorted()))
  }

  // Extract current entries for comparison
  val moduleNames = info.entries.filterIsInstance<DepEntry.Module>().map { it.name }
  val pluginIds = info.entries.filterIsInstance<DepEntry.Plugin>().map { it.id }

  // Manual entries = manual plugins + manual modules (in original order)
  val manualEntries = info.entries.filter { entry ->
    when (entry) {
      is DepEntry.Plugin -> preserveExistingPlugin?.invoke(entry.id) == true
      is DepEntry.Module -> preserveExistingModule?.invoke(entry.name) == true
    }
  }
  val manualModuleNames = manualEntries.filterIsInstance<DepEntry.Module>().map { it.name }
  val manualPluginIds = manualEntries.filterIsInstance<DepEntry.Plugin>().map { it.id }

  // Filter out xi:include deps - they're already present in xi:included files, don't duplicate in main file
  val xiModuleValues = xiIncludeModuleDeps.mapTo(HashSet()) { it.value }
  val xiPluginValues = xiIncludePluginDeps.mapTo(HashSet()) { it.value }
  val autoModules = (moduleDependencies.toSet() - xiModuleValues).sorted()
  val autoPlugins = (pluginDependencies.toSet() - xiPluginValues).sorted()

  // For INSIDE_SECTION: filter out deps that are manually declared outside the region
  // to avoid duplicating them in the generated region
  val (effectiveAutoModules, effectiveAutoPlugins) = if (info.regionType == RegionType.INSIDE_SECTION) {
    val modulesInRegion = info.entriesInRegion.filterIsInstance<DepEntry.Module>().map { it.name }.toSet()
    val pluginsInRegion = info.entriesInRegion.filterIsInstance<DepEntry.Plugin>().map { it.id }.toSet()
    val manualModulesOutsideRegion = moduleNames.toSet() - modulesInRegion
    val manualPluginsOutsideRegion = pluginIds.toSet() - pluginsInRegion
    Pair(
      autoModules.filter { it !in manualModulesOutsideRegion },
      autoPlugins.filter { it !in manualPluginsOutsideRegion }
    )
  }
  else {
    Pair(autoModules, autoPlugins)
  }

  // Skip writing if both module set and plugin set are unchanged AND file already uses current region format
  // Force rewrite if file uses legacy editor-fold markers or old region text without re-gen instructions
  val usesLegacyMarkers = content.contains(LEGACY_MARKER) && !content.contains(REGION_MARKER)
  val usesOldRegionText = content.contains(REGION_MARKER) && !content.contains(REGION_START)
  val modulesUnchanged = moduleNames.sorted() == (effectiveAutoModules + manualModuleNames).distinct().sorted()
  val pluginsUnchanged = pluginIds.sorted() == (effectiveAutoPlugins + manualPluginIds).distinct().sorted()

  if (modulesUnchanged && pluginsUnchanged && !usesLegacyMarkers && !usesOldRegionText) {
    return FileChangeStatus.UNCHANGED
  }

  val replacement = when {
    effectiveAutoModules.isEmpty() && effectiveAutoPlugins.isEmpty() && manualEntries.isEmpty() -> ""
    else -> when (info.regionType) {
      RegionType.WRAPS_ENTIRE_SECTION -> buildFullBlock(info.indent, manualEntries, autoModules, autoPlugins)
      RegionType.INSIDE_SECTION -> {
        // Include manual entries that were inside the region (suppressed deps)
        val manualModulesInRegion = manualEntries
          .filterIsInstance<DepEntry.Module>()
          .filter { it in info.entriesInRegion }
          .map { it.name }
        val manualPluginsInRegion = manualEntries
          .filterIsInstance<DepEntry.Plugin>()
          .filter { it in info.entriesInRegion }
          .map { it.id }
        buildFoldOnly(
          info.indent,
          effectiveAutoModules + manualModulesInRegion,
          effectiveAutoPlugins + manualPluginsInRegion
        )
      }
      RegionType.NONE -> buildWithEntries(info.indent, manualEntries, autoModules, autoPlugins)
    }
  }

  return strategy.writeIfChanged(path = path, oldContent = content, newContent = content.substring(0, info.startOffset) + replacement + content.substring(info.endOffset))
}

/**
 * Extracts dependency entries from the first <dependencies> section using the same parsing
 * logic as [updateXmlDependencies]. Returns null if no <dependencies> section exists.
 */
internal fun extractDependenciesEntries(content: String): ParsedDependenciesEntries? {
  val info = parseDependenciesInfo(content) ?: return null
  val moduleNames = info.entries.filterIsInstance<DepEntry.Module>().map { it.name }
  val pluginIds = info.entries.filterIsInstance<DepEntry.Plugin>().map { it.id }
  return ParsedDependenciesEntries(moduleNames = moduleNames, pluginIds = pluginIds)
}

private fun parseDependenciesInfo(content: String): DependenciesInfo? {
  // Find <dependencies> section using StAX
  var depsStart = -1
  var depsLineStart = -1
  var depsEnd = -1
  var depsIndent = ""
  val entries = mutableListOf<DepEntry>()
  // Track character offset for each entry to determine if it's inside the region
  val entryOffsets = mutableListOf<Int>()

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
            val offset = reader.location.characterOffset
            when (reader.localName) {
              "plugin" -> reader.getAttributeValue(null, "id")?.let {
                entries.add(DepEntry.Plugin(it))
                entryOffsets.add(offset)
              }
              "module" -> reader.getAttributeValue(null, "name")?.let {
                entries.add(DepEntry.Module(it))
                entryOffsets.add(offset)
              }
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

  // Check region/editor-fold position relative to <dependencies>
  var foldStart = content.indexOf(REGION_MARKER)
  if (foldStart == -1) {
    foldStart = content.indexOf(LEGACY_MARKER)
  }
  if (foldStart == -1) {
    return DependenciesInfo(depsLineStart, depsEnd, entries, emptySet(), depsIndent, RegionType.NONE)
  }

  val fold = findFoldRegion(content, foldStart)
             ?: return DependenciesInfo(depsLineStart, depsEnd, entries, emptySet(), depsIndent, RegionType.NONE)

  // Determine which entries are inside the generated region
  val entriesInRegion = entries.filterIndexedTo(HashSet()) { index, _ ->
    val offset = entryOffsets[index]
    offset >= fold.startOffset && offset < fold.endOffset
  }

  return if (foldStart < depsStart) {
    // Editor-fold wraps entire section (module descriptors)
    DependenciesInfo(fold.startOffset, fold.endOffset, entries, entriesInRegion, fold.indent, RegionType.WRAPS_ENTIRE_SECTION)
  }
  else {
    // Editor-fold inside section (plugin.xml)
    DependenciesInfo(fold.startOffset, fold.endOffset, entries, entriesInRegion, fold.indent, RegionType.INSIDE_SECTION)
  }
}

private fun insertDependenciesSection(content: String, modules: List<String>, plugins: List<String>): String {
  val pos = content.indexOf('>', content.indexOf("<idea-plugin"))
  if (pos == -1) return content

  val nextLine = content.indexOf('\n', pos + 1)
  val indent = (if (nextLine != -1) content.substring(nextLine + 1).takeWhile { it == ' ' || it == '\t' } else "").ifEmpty { "  " }
  val suffix = content.substring(pos + 1).let { if (it.startsWith('\n')) it.substring(1) else it }
  return content.substring(0, pos + 1) + "\n" + buildFullBlock(indent, emptyList(), modules, plugins) + suffix
}

/** Region only with auto-generated modules/plugins (for INSIDE_SECTION - replacing fold content) */
private fun buildFoldOnly(indent: String, autoModules: List<String>, autoPlugins: List<String>): String {
  if (autoModules.isEmpty() && autoPlugins.isEmpty()) return ""
  return StringBuilder().apply {
    append(indent).append(REGION_START).append("\n")
    appendPlugins(indent, autoPlugins)
    appendModules(indent, autoModules)
    append(indent).append(REGION_END).append("\n")
  }.toString()
}

/** Full region block with <dependencies> (for WRAPS_ENTIRE_SECTION - module descriptors) */
private fun buildFullBlock(indent: String, manualEntries: List<DepEntry>, autoModules: List<String>, autoPlugins: List<String>): String {
  val manualPlugins = manualEntries.filterIsInstance<DepEntry.Plugin>().map { it.id }
  val manualModules = manualEntries.filterIsInstance<DepEntry.Module>().map { it.name }
  return StringBuilder().apply {
    append(indent).append(REGION_START).append("\n")
    append(indent).append("<dependencies>\n")
    appendPlugins("$indent  ", manualPlugins)
    appendPlugins("$indent  ", autoPlugins)
    appendModules("$indent  ", manualModules)
    appendModules("$indent  ", autoModules)
    append(indent).append("</dependencies>\n")
    append(indent).append(REGION_END).append("\n")
  }.toString()
}

/** Full <dependencies> section preserving original order of plugins and manual modules (for `NONE` case) */
private fun buildWithEntries(indent: String, manualEntries: List<DepEntry>, autoModules: List<String>, autoPlugins: List<String>): String {
  return StringBuilder().apply {
    append(indent).append("<dependencies>\n")
    for (entry in manualEntries) {
      when (entry) {
        is DepEntry.Plugin -> append(indent).append("  <plugin id=\"").append(entry.id).append("\"/>\n")
        is DepEntry.Module -> append(indent).append("  <module name=\"").append(entry.name).append("\"/>\n")
      }
    }
    if (autoPlugins.isNotEmpty() || autoModules.isNotEmpty()) {
      append(indent).append("  ").append(REGION_START).append("\n")
      appendPlugins("$indent  ", autoPlugins)
      appendModules("$indent  ", autoModules)
      append(indent).append("  ").append(REGION_END).append("\n")
    }
    append(indent).append("</dependencies>\n")
  }.toString()
}

/**
 * Result of migrating legacy `<depends>` entries to `<plugin id="..."/>` format.
 */
internal data class LegacyMigrationResult(
  /** XML content with `<depends>` entries removed */
  @JvmField val content: String,
  /** Plugin IDs to add as `<plugin id="..."/>` dependencies */
  @JvmField val pluginDepsToAdd: List<String>,
)
