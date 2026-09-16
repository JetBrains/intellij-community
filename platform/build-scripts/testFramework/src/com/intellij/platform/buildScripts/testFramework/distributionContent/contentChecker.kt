// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions", "GrazieInspection")

package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import org.assertj.core.util.diff.DiffUtils
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path

@Internal
fun buildUnifiedDiffText(fileName: String, originalLines: List<String>, revisedLines: List<String>): String {
  val patch = DiffUtils.diff(originalLines, revisedLines)
  return DiffUtils.generateUnifiedDiff(fileName, fileName, originalLines, patch, 3).joinToString(separator = "\n")
}

/**
 * A failure that carries a patch over one checked-in file.
 *
 * [currentText] is what the file holds, and [desiredText] is what it must hold. The message states the patch, so a
 * reader of a log can apply it. The error states the desired text as the expected side and names the real file as the
 * actual one, so the Diff Viewer of the IDE writes the desired text into that file.
 *
 * A file the patch creates is created empty here, because `FileComparisonFailedError` takes a path that exists.
 */
@Internal
fun createFilePatchFailure(
  name: String,
  file: Path,
  projectHome: Path,
  currentText: String,
  desiredText: String,
  context: String,
): PackagingCheckFailure {
  val patchText = buildUnifiedDiffText(
    fileName = projectHome.relativize(file).toString(),
    originalLines = currentText.lines(),
    revisedLines = desiredText.lines(),
  )
  if (!Files.exists(file)) {
    file.parent?.let(Files::createDirectories)
    Files.createFile(file)
  }
  return PackagingCheckFailure(
    name = name,
    error = FileComparisonFailedError(
      message = buildString {
        appendLine(context)
        appendLine()
        // The patch names the file on both sides without a prefix, so `git apply` needs `-p0`.
        appendLine("Patch, to apply with `git apply -p0` or to accept in the Diff Viewer:")
        appendLine(patchText)
      },
      expected = desiredText,
      actual = currentText,
      actualFilePath = file.toString(),
    ),
  )
}

@Internal
data class ParsedContentReport(
  @JvmField val platform: List<FileEntry>,
  @JvmField val productModules: List<PluginContentReport>,
  @JvmField val bundled: List<PluginContentReport>,
  @JvmField val nonBundled: List<PluginContentReport>,
)

@Internal
data class PackagingCheckFailure(
  @JvmField val name: String,
  @JvmField val error: Throwable,
)

/**
 * The check over the plugins a build reported.
 *
 * A plugin has no checked-in snapshot, so a plugin gets one check. The content the build reports for a bundled plugin
 * must equal the content it reports for the non-bundled build of the same plugin. This finds a layout that differs
 * between the two builds. It does not find a change of one plugin's own content, and it asks no reviewer to approve
 * such a change.
 *
 * A plugin the build reports only as non-bundled therefore gets no check, and no test name is stated for one.
 */
@Internal
fun collectPluginContentFailures(
  content: ParsedContentReport,
  checkPlugins: Boolean = true,
  testName: (category: String, key: String) -> String,
): List<PackagingCheckFailure> {
  if (!checkPlugins) {
    return emptyList()
  }
  return collectBundledPluginContentFailures(
    bundled = toPluginContentMap(content.bundled).values,
    nonBundled = toPluginContentMap(content.nonBundled),
    testName = { key -> testName("bundled-plugin", key) },
  )
}

/**
 * Compares the content of each bundled plugin against the content of the non-bundled build of the same plugin.
 *
 * The two builds pack one plugin, so one layout answers for both. A plugin the non-bundled build does not report is
 * skipped, because there is no second answer to compare with.
 */
private fun collectBundledPluginContentFailures(
  bundled: Collection<PluginContentReport>,
  nonBundled: Map<String, PluginContentReport>,
  testName: (key: String) -> String,
): List<PackagingCheckFailure> {
  val failures = ArrayList<PackagingCheckFailure>()
  for ((_, items) in bundled.groupBy { it.mainModule }) {
    val key = getPluginContentKey(items.first())
    val nonBundledVersion = nonBundled.get(key) ?: continue
    try {
      val bundledContent = normalizeContentReport(fileEntries = mergePerOsPluginContent(items), short = true)
      val nonBundledContent = normalizeContentReport(fileEntries = nonBundledVersion.content, short = true)
      if (bundledContent != nonBundledContent) {
        throw AssertionError(
          "Bundled plugin content must be equal to non-bundled one." +
          "\nbundled:\n$bundledContent" +
          "\nnon-bundled:\n$nonBundledContent"
        )
      }
    }
    catch (t: Throwable) {
      failures.add(PackagingCheckFailure(name = testName(key), error = t))
    }
  }
  return failures
}

/**
 * One plugin's content over every target platform the build reported it for, as one list.
 *
 * A plugin gets a report per operating system and architecture where its layout differs by one. The android plugin
 * excludes some module libraries that way. Each caller compares one plugin against one other answer, so it needs one
 * list per plugin, and the variants are unioned rather than compared with each other.
 *
 * **The union deduplicates on the same values the comparison uses.** Every caller compares `short = true` entries, which
 * is what makes a change of `ProjectLibraryEntry.dependentModules` alone not a change. A union that deduplicated
 * `short = false` entries kept two variants that differ in that field only, and the comparison then saw one jar twice
 * where the other side names it once. That is a failure about a field the comparison had already declined to look at.
 *
 * A single variant is returned unnormalized, because the caller normalizes what it is given and doing it twice says
 * nothing.
 *
 * What this still does not do is state a superset of two entries that really differ. Two variants whose `lib/x.jar`
 * holds different module libraries stay two entries with one name, and the comparison fails naming that jar.
 */
@Internal
fun mergePerOsPluginContent(items: List<PluginContentReport>): List<FileEntry> {
  if (items.size == 1) {
    return items.first().content
  }
  return items.flatMap { normalizeContentReport(fileEntries = it.content, short = true) }.distinct()
}

private fun toPluginContentMap(contentList: List<PluginContentReport>): Map<String, PluginContentReport> {
  val result = LinkedHashMap<String, PluginContentReport>(contentList.size)
  for (item in contentList) {
    val key = getPluginContentKey(item)
    check(result.put(key, item) == null) { "Duplicate plugin content entries: $key" }
  }
  return result
}

private fun getPluginContentKey(item: PluginContentReport): String {
  return item.mainModule +
         (if (item.os == null) "" else " (os=${item.os})") +
         (if (item.arch == null) "" else " (arch=${item.arch})")
}

internal fun normalizeContentReport(fileEntries: List<FileEntry>, short: Boolean): List<FileEntry> {
  return fileEntries.map { originalItem ->
    val item = originalItem.copy(
      modules = originalItem.modules.map { normalizeModuleEntry(it) },
      contentModules = originalItem.contentModules.map { normalizeModuleEntry(it) },
      projectLibraries = originalItem.projectLibraries.map { projectLib ->
        projectLib.copy(
          files = projectLib.files.map { it.copy(name = removeVersionFromName(it.name), size = 0) },
          dependentModules = projectLib.dependentModules.takeIf { !short } ?: emptyMap(),
        )
      },
      files = originalItem.files.map { it.copy(name = removeVersionFromName(it.name), size = 0) },
    )
    item
  }
}

private fun normalizeModuleEntry(moduleEntry: ModuleEntry): ModuleEntry {
  return moduleEntry.copy(
    size = 0,
    libraries = moduleEntry.libraries.mapValues { mapEntry ->
      mapEntry.value.map {
        it.copy(name = removeVersionFromName(it.name), size = 0)
      }
    },
    reason = moduleEntry.reason?.takeIf { !it.startsWith("withModule at") },
  )
}

private val versionRegex = Regex("([/-])(\\d+)(\\.\\d+){1,2}[0-9a-zA-Z\\-_.]*(/|.jar)")

private fun removeVersionFromName(name: String): String {
  return versionRegex.replace(name) {
    val groups = it.groups
    groups[1]!!.value + groups[2]!!.value + groups.last()!!.value
  }
}
