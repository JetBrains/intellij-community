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

private data class RemovalRange(
  @JvmField val start: Int,
  @JvmField val end: Int,
)

private sealed class DepEntry {
  data class Plugin(val id: String) : DepEntry()
  data class Module(val name: String) : DepEntry()
}

private data class DepOccurrence(
  @JvmField val entry: DepEntry,
  @JvmField val startOffset: Int,
  @JvmField val inRegion: Boolean,
)

internal data class ParsedDependenciesEntries(
  @JvmField val moduleNames: List<String>,
  @JvmField val pluginIds: List<String>,
)

private class DependenciesInfo(
  @JvmField val startOffset: Int,
  @JvmField val endOffset: Int,
  @JvmField val entries: List<DepOccurrence>,  // All entries in original order with region membership
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
 * @param allowInsideSectionRegion Whether region markers inside `<dependencies>` should be treated as generated sub-blocks.
 *        Keep `true` for real plugin.xml files; use `false` for content module descriptors to avoid preserving
 *        manual dependencies outside the generated region.
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
  allowInsideSectionRegion: Boolean = true,
  strategy: FileUpdateStrategy,
): FileChangeStatus {
  val updatedContent = buildUpdatedXmlDependenciesContent(
    content = content,
    moduleDependencies = moduleDependencies,
    pluginDependencies = pluginDependencies,
    preserveExistingModule = preserveExistingModule,
    preserveExistingPlugin = preserveExistingPlugin,
    legacyPluginDependencies = legacyPluginDependencies,
    xiIncludeModuleDeps = xiIncludeModuleDeps,
    xiIncludePluginDeps = xiIncludePluginDeps,
    allowInsideSectionRegion = allowInsideSectionRegion,
  ) ?: return FileChangeStatus.UNCHANGED

  return strategy.writeIfChanged(path = path, oldContent = content, newContent = updatedContent)
}

internal fun buildUpdatedXmlDependenciesContent(
  content: String,
  moduleDependencies: List<String>,
  pluginDependencies: List<String> = emptyList(),
  preserveExistingModule: ((String) -> Boolean)? = null,
  preserveExistingPlugin: ((String) -> Boolean)? = null,
  legacyPluginDependencies: List<String> = emptyList(),
  xiIncludeModuleDeps: Set<ContentModuleName> = emptySet(),
  xiIncludePluginDeps: Set<PluginId> = emptySet(),
  allowInsideSectionRegion: Boolean = true,
): String? {
  if (content.isEmpty()) {
    return null
  }

  val info = parseDependenciesInfo(content, allowInsideSectionRegion)
  if (info == null) {
    if (moduleDependencies.isEmpty() && pluginDependencies.isEmpty()) {
      return null
    }
    // Compare with legacy <depends> - if semantically same, don't convert format
    if (moduleDependencies.isEmpty() && legacyPluginDependencies.isNotEmpty()) {
      if (pluginDependencies.sorted() == legacyPluginDependencies.sorted()) {
        return null
      }
    }
    // Check if all auto-derived deps are covered by xi:includes - if so, no need to insert in main file
    val xiIncludeModuleValues = xiIncludeModuleDeps.mapTo(HashSet()) { it.value }
    val xiIncludePluginValues = xiIncludePluginDeps.mapTo(HashSet()) { it.value }
    val uncoveredModules = moduleDependencies.toSet() - xiIncludeModuleValues
    val uncoveredPlugins = pluginDependencies.toSet() - xiIncludePluginValues
    if (uncoveredModules.isEmpty() && uncoveredPlugins.isEmpty()) {
      return null  // All deps in xi:includes, no modification needed
    }
    // Only insert deps NOT covered by xi:includes
    return insertDependenciesSection(content, uncoveredModules.sorted(), uncoveredPlugins.sorted())
  }

  val entries = info.entries

  // Extract current entries for comparison
  val moduleNames = entries.mapNotNull { (it.entry as? DepEntry.Module)?.name }
  val pluginIds = entries.mapNotNull { (it.entry as? DepEntry.Plugin)?.id }

  // Manual entries = preserved plugins/modules (in original order)
  val manualEntries = entries.filter { occurrence ->
    when (val entry = occurrence.entry) {
      is DepEntry.Plugin -> preserveExistingPlugin?.invoke(entry.id) == true
      is DepEntry.Module -> preserveExistingModule?.invoke(entry.name) == true
    }
  }

  // Filter out xi:include deps - they're already present in xi:included files, don't duplicate in main file
  val xiModuleValues = xiIncludeModuleDeps.mapTo(HashSet()) { it.value }
  val xiPluginValues = xiIncludePluginDeps.mapTo(HashSet()) { it.value }
  val autoModules = (moduleDependencies.toSet() - xiModuleValues).sorted()
  val autoPlugins = (pluginDependencies.toSet() - xiPluginValues).sorted()

  val manualEntriesInRegion = manualEntries.filter { it.inRegion }
  val generatedRegionModules = dedupeValuesKeepingFirst(
    autoModules + manualEntriesInRegion.mapNotNull { (it.entry as? DepEntry.Module)?.name }
  )
  val generatedRegionPlugins = dedupeValuesKeepingFirst(
    autoPlugins + manualEntriesInRegion.mapNotNull { (it.entry as? DepEntry.Plugin)?.id }
  )

  if (info.regionType == RegionType.INSIDE_SECTION) {
    val outsideRegionEntries = entries.filterNot { it.inRegion }
    val duplicateRanges = collectDuplicateRemovalRanges(
      content = content,
      entries = outsideRegionEntries,
      generatedModuleNames = generatedRegionModules.toSet(),
      generatedPluginIds = generatedRegionPlugins.toSet(),
    )
    if (duplicateRanges.isNotEmpty()) {
      val cleanedContent = removeRanges(content, duplicateRanges)
      return buildUpdatedXmlDependenciesContent(
        content = cleanedContent,
        moduleDependencies = moduleDependencies,
        pluginDependencies = pluginDependencies,
        preserveExistingModule = preserveExistingModule,
        preserveExistingPlugin = preserveExistingPlugin,
        legacyPluginDependencies = legacyPluginDependencies,
        xiIncludeModuleDeps = xiIncludeModuleDeps,
        xiIncludePluginDeps = xiIncludePluginDeps,
        allowInsideSectionRegion = allowInsideSectionRegion,
      ) ?: cleanedContent
    }
  }

  val retainedOutsideEntries = if (info.regionType == RegionType.INSIDE_SECTION) {
    dedupeOccurrencesKeepingFirst(
      entries = entries.filterNot { it.inRegion },
      generatedModuleNames = generatedRegionModules.toSet(),
      generatedPluginIds = generatedRegionPlugins.toSet(),
    )
  }
  else {
    emptyList()
  }

  val normalizedManualEntries = if (info.regionType == RegionType.INSIDE_SECTION) {
    emptyList()
  }
  else {
    dedupeOccurrencesKeepingFirst(
      entries = manualEntries,
      generatedModuleNames = autoModules.toSet(),
      generatedPluginIds = autoPlugins.toSet(),
    )
  }

  val expectedModuleNames = when (info.regionType) {
    RegionType.INSIDE_SECTION -> retainedOutsideEntries.filterIsInstance<DepEntry.Module>().map { it.name } + generatedRegionModules
    else -> normalizedManualEntries.filterIsInstance<DepEntry.Module>().map { it.name } + autoModules
  }
  val expectedPluginIds = when (info.regionType) {
    RegionType.INSIDE_SECTION -> retainedOutsideEntries.filterIsInstance<DepEntry.Plugin>().map { it.id } + generatedRegionPlugins
    else -> normalizedManualEntries.filterIsInstance<DepEntry.Plugin>().map { it.id } + autoPlugins
  }

  // Skip writing if both module set and plugin set are unchanged AND file already uses current region format
  // Force rewrite if file uses legacy editor-fold markers or old region text without re-gen instructions
  val usesLegacyMarkers = content.contains(LEGACY_MARKER) && !content.contains(REGION_MARKER)
  val usesOldRegionText = content.contains(REGION_MARKER) && !content.contains(REGION_START)
  val modulesUnchanged = moduleNames.sorted() == expectedModuleNames.distinct().sorted()
  val pluginsUnchanged = pluginIds.sorted() == expectedPluginIds.distinct().sorted()
  val hasDuplicateModules = moduleNames.size != moduleNames.distinct().size
  val hasDuplicatePlugins = pluginIds.size != pluginIds.distinct().size

  if (modulesUnchanged && pluginsUnchanged && !usesLegacyMarkers && !usesOldRegionText && !hasDuplicateModules && !hasDuplicatePlugins) {
    return null
  }

  val replacement = when {
    when (info.regionType) {
      RegionType.INSIDE_SECTION -> generatedRegionModules.isEmpty() && generatedRegionPlugins.isEmpty()
      else -> autoModules.isEmpty() && autoPlugins.isEmpty() && normalizedManualEntries.isEmpty()
    } -> ""
    else -> when (info.regionType) {
      RegionType.WRAPS_ENTIRE_SECTION -> buildFullBlock(info.indent, normalizedManualEntries, autoModules, autoPlugins)
      RegionType.INSIDE_SECTION -> buildFoldOnly(info.indent, generatedRegionModules, generatedRegionPlugins)
      RegionType.NONE -> buildWithEntries(info.indent, normalizedManualEntries, autoModules, autoPlugins)
    }
  }

  return content.substring(0, info.startOffset) + replacement + content.substring(info.endOffset)
}

/**
 * Extracts dependency entries from the first <dependencies> section using the same parsing
 * logic as [updateXmlDependencies]. Returns null if no <dependencies> section exists.
 */
internal fun extractDependenciesEntries(content: String): ParsedDependenciesEntries? {
  val info = parseDependenciesInfo(content = content, allowInsideSectionRegion = true) ?: return null
  val moduleNames = info.entries.mapNotNull { (it.entry as? DepEntry.Module)?.name }
  val pluginIds = info.entries.mapNotNull { (it.entry as? DepEntry.Plugin)?.id }
  return ParsedDependenciesEntries(moduleNames = moduleNames, pluginIds = pluginIds)
}

private fun parseDependenciesInfo(content: String, allowInsideSectionRegion: Boolean): DependenciesInfo? {
  // Find <dependencies> section using StAX
  var depsStart = -1
  var depsLineStart = -1
  var depsEnd = -1
  var depsIndent = ""
  val entries = mutableListOf<DepEntry>()
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
            when (reader.localName) {
              "plugin" -> reader.getAttributeValue(null, "id")?.let {
                val startOffset = content.lastIndexOf("<plugin", reader.location.characterOffset)
                entries.add(DepEntry.Plugin(it))
                entryOffsets.add(startOffset)
              }
              "module" -> reader.getAttributeValue(null, "name")?.let {
                val startOffset = content.lastIndexOf("<module", reader.location.characterOffset)
                entries.add(DepEntry.Module(it))
                entryOffsets.add(startOffset)
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
    return DependenciesInfo(
      startOffset = depsLineStart,
      endOffset = depsEnd,
      entries = entries.mapIndexed { index, entry ->
        DepOccurrence(entry = entry, startOffset = entryOffsets[index], inRegion = false)
      },
      indent = depsIndent,
      regionType = RegionType.NONE,
    )
  }

  val fold = findFoldRegion(content, foldStart)
             ?: return DependenciesInfo(
               startOffset = depsLineStart,
               endOffset = depsEnd,
               entries = entries.mapIndexed { index, entry ->
                 DepOccurrence(entry = entry, startOffset = entryOffsets[index], inRegion = false)
               },
               indent = depsIndent,
               regionType = RegionType.NONE,
             )

  // Determine which entries are inside the generated region
  val occurrences = entries.mapIndexed { index, entry ->
    val offset = entryOffsets[index]
    DepOccurrence(
      entry = entry,
      startOffset = offset,
      inRegion = offset >= fold.startOffset && offset < fold.endOffset,
    )
  }

  return if (foldStart < depsStart) {
    // Editor-fold wraps entire section (module descriptors)
    DependenciesInfo(fold.startOffset, fold.endOffset, occurrences, fold.indent, RegionType.WRAPS_ENTIRE_SECTION)
  }
  else {
    if (allowInsideSectionRegion) {
      // Editor-fold inside section (plugin.xml)
      DependenciesInfo(fold.startOffset, fold.endOffset, occurrences, fold.indent, RegionType.INSIDE_SECTION)
    }
    else {
      // Normalize non-plugin descriptors to whole-section replacement.
      DependenciesInfo(depsLineStart, depsEnd, occurrences, depsIndent, RegionType.WRAPS_ENTIRE_SECTION)
    }
  }
}

private fun dedupeValuesKeepingFirst(values: List<String>): List<String> {
  val seen = HashSet<String>()
  return values.filterTo(ArrayList(values.size)) { seen.add(it) }
}

private fun dedupeOccurrencesKeepingFirst(
  entries: List<DepOccurrence>,
  generatedModuleNames: Set<String>,
  generatedPluginIds: Set<String>,
): List<DepEntry> {
  val seenModules = HashSet<String>()
  val seenPlugins = HashSet<String>()
  val result = ArrayList<DepEntry>(entries.size)
  for (occurrence in entries) {
    val entry = occurrence.entry
    when (entry) {
      is DepEntry.Module -> {
        if (entry.name in generatedModuleNames || !seenModules.add(entry.name)) continue
      }
      is DepEntry.Plugin -> {
        if (entry.id in generatedPluginIds || !seenPlugins.add(entry.id)) continue
      }
    }
    result.add(entry)
  }
  return result
}

private fun collectDuplicateRemovalRanges(
  content: String,
  entries: List<DepOccurrence>,
  generatedModuleNames: Set<String>,
  generatedPluginIds: Set<String>,
): List<RemovalRange> {
  val seenModules = HashSet<String>()
  val seenPlugins = HashSet<String>()
  val ranges = ArrayList<RemovalRange>()
  for (occurrence in entries) {
    val shouldRemove = when (val entry = occurrence.entry) {
      is DepEntry.Module -> entry.name in generatedModuleNames || !seenModules.add(entry.name)
      is DepEntry.Plugin -> entry.id in generatedPluginIds || !seenPlugins.add(entry.id)
    }
    if (shouldRemove) {
      createEntryRemovalRange(content, occurrence)?.let(ranges::add)
    }
  }
  return ranges
}

private fun createEntryRemovalRange(content: String, occurrence: DepOccurrence): RemovalRange? {
  val tagName = when (occurrence.entry) {
    is DepEntry.Module -> "module"
    is DepEntry.Plugin -> "plugin"
  }
  val startOffset = occurrence.startOffset
  if (startOffset < 0) return null

  var start = startOffset
  val lineStart = content.lastIndexOf('\n', startOffset - 1).let { if (it == -1) 0 else it + 1 }
  if (content.substring(lineStart, startOffset).all { it == ' ' || it == '\t' }) {
    start = lineStart
  }

  val elementEnd = findElementEndOffset(content, tagName, startOffset) ?: return null
  var end = elementEnd
  if (end < content.length && content[end] == '\r') end++
  if (end < content.length && content[end] == '\n') end++
  return RemovalRange(start = start, end = end)
}

private fun removeRanges(content: String, ranges: List<RemovalRange>): String {
  if (ranges.isEmpty()) {
    return content
  }

  val sortedRanges = ranges.sortedBy { it.start }
  val result = StringBuilder(content.length)
  var cursor = 0
  for (range in sortedRanges) {
    if (range.start < cursor) continue
    result.append(content, cursor, range.start)
    cursor = range.end
  }
  result.append(content, cursor, content.length)
  return result.toString()
}

private fun insertDependenciesSection(content: String, modules: List<String>, plugins: List<String>): String {
  val pos = content.indexOf('>', content.indexOf("<idea-plugin"))
  if (pos == -1) return content

  val nextLine = content.indexOf('\n', pos + 1)
  val indent = (if (nextLine != -1) content.substring(nextLine + 1).takeWhile { it == ' ' || it == '\t' } else "").ifEmpty { "  " }
  val insertPos = findDependenciesInsertPos(content, pos + 1)
  val prefix = content.substring(0, insertPos)
  val suffix = content.substring(insertPos)
  val normalizedPrefix = if (prefix.isNotEmpty() && prefix.last() != '\n') "$prefix\n" else prefix
  val normalizedSuffix = suffix.removeSingleLeadingLineBreak()
  return normalizedPrefix + buildFullBlock(indent, emptyList(), modules, plugins) + normalizedSuffix
}

private fun String.removeSingleLeadingLineBreak(): String {
  return when {
    startsWith("\r\n") -> substring(2)
    startsWith("\n") -> substring(1)
    else -> this
  }
}

private val METADATA_TAGS = setOf(
  "id",
  "name",
  "description",
  "category",
  "vendor",
  "version",
  "idea-version",
  "change-notes",
)

private fun findDependenciesInsertPos(content: String, rootContentStart: Int): Int {
  val metadataEnd = findMetadataEndOffset(content) ?: return rootContentStart
  var pos = content.indexOf('\n', metadataEnd).let { if (it == -1) content.length else it + 1 }
  while (pos < content.length) {
    val lineEnd = content.indexOf('\n', pos).let { if (it == -1) content.length else it + 1 }
    if (content.substring(pos, lineEnd).trim().isNotEmpty()) break
    pos = lineEnd
  }
  return pos
}

private fun findMetadataEndOffset(content: String): Int? {
  val reader = createXmlStreamReaderWithLocation(StringReader(content))
  try {
    var depth = 0
    var lastEnd = -1
    while (reader.hasNext()) {
      when (reader.next()) {
        XMLStreamConstants.START_ELEMENT -> {
          val localName = reader.localName
          if (depth == 0 && localName == "idea-plugin") {
            depth = 1
            continue
          }
          if (depth > 0) {
            val currentDepth = depth
            depth++
            if (currentDepth == 1 && METADATA_TAGS.contains(localName)) {
              val startOffset = content.lastIndexOf("<$localName", reader.location.characterOffset)
              if (startOffset != -1) {
                val endOffset = findElementEndOffset(content, localName, startOffset)
                if (endOffset != null && endOffset > lastEnd) {
                  lastEnd = endOffset
                }
              }
            }
          }
        }
        XMLStreamConstants.END_ELEMENT -> if (depth > 0) depth--
      }
    }
    return if (lastEnd == -1) null else lastEnd
  }
  finally {
    reader.close()
  }
}

private fun findElementEndOffset(content: String, tag: String, startOffset: Int): Int? {
  val startTagEnd = content.indexOf('>', startOffset)
  if (startTagEnd == -1) return null
  var i = startTagEnd - 1
  while (i >= startOffset && content[i].isWhitespace()) i--
  val selfClosing = i >= startOffset && content[i] == '/'
  if (selfClosing) return startTagEnd + 1
  val closeTag = "</$tag>"
  val closeIndex = content.indexOf(closeTag, startTagEnd + 1)
  return if (closeIndex == -1) null else closeIndex + closeTag.length
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
  /** XML content with duplicate legacy `<depends>` entries removed */
  @JvmField val content: String,
  /** Legacy plugin IDs removed from the content */
  @JvmField val removedLegacyPluginIds: Set<PluginId> = emptySet(),
)

/**
 * Removes legacy `<depends>` entries that duplicate modern `<dependencies><plugin/>` declarations.
 * Only non-optional entries without config-file are removed.
 */
internal fun removeDuplicateLegacyDepends(content: String, modernPluginIds: Set<PluginId>): LegacyMigrationResult {
  if (modernPluginIds.isEmpty() || !content.contains("<depends")) {
    return LegacyMigrationResult(content = content)
  }

  val idsToRemove = modernPluginIds.mapTo(HashSet()) { it.value }
  val ranges = ArrayList<RemovalRange>()
  val removed = LinkedHashSet<PluginId>()

  val reader = createXmlStreamReaderWithLocation(StringReader(content))
  try {
    while (reader.hasNext()) {
      when (reader.next()) {
        XMLStreamConstants.START_ELEMENT -> if (reader.localName == "depends") {
          val optionalAttr = reader.getAttributeValue(null, "optional")
          val configFileAttr = reader.getAttributeValue(null, "config-file")
          val isOptional = optionalAttr?.equals("true", ignoreCase = true) == true
          val hasConfigFile = !configFileAttr.isNullOrEmpty()
          val startOffset = content.lastIndexOf("<depends", reader.location.characterOffset)
          val pluginId = reader.elementText.trim()
          if (!isOptional && !hasConfigFile && startOffset != -1 && pluginId in idsToRemove) {
            var start = startOffset
            val lineStart = content.lastIndexOf('\n', startOffset - 1).let { if (it == -1) 0 else it + 1 }
            if (content.substring(lineStart, startOffset).all { it == ' ' || it == '\t' }) {
              start = lineStart
            }

            val closeIndex = content.indexOf("</depends>", startOffset)
            if (closeIndex != -1) {
              var end = closeIndex + "</depends>".length
              if (end < content.length && content[end] == '\r') end++
              if (end < content.length && content[end] == '\n') end++
              ranges.add(RemovalRange(start, end))
              removed.add(PluginId(pluginId))
            }
          }
        }
      }
    }
  }
  finally {
    reader.close()
  }

  if (ranges.isEmpty()) {
    return LegacyMigrationResult(content = content)
  }

  ranges.sortBy { it.start }
  val result = StringBuilder(content.length)
  var cursor = 0
  for (range in ranges) {
    if (range.start < cursor) continue
    result.append(content, cursor, range.start)
    cursor = range.end
  }
  result.append(content, cursor, content.length)
  return LegacyMigrationResult(content = result.toString(), removedLegacyPluginIds = removed)
}
