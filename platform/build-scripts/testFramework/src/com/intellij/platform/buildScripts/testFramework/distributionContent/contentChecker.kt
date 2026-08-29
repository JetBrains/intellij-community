// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions", "GrazieInspection")

package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.ModuleEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.deserializeContentData
import com.intellij.platform.distributionContent.deserializePluginData
import com.intellij.platform.distributionContent.serializeContentEntries
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.util.lang.HashMapZipFile
import kotlinx.serialization.SerializationException
import org.assertj.core.util.diff.DiffUtils
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

private const val ADDITIONAL_INSTRUCTIONS = """
Snapshots for other products may require update, please run 'All Packaging Tests' run configuration to run all packaging tests.

When the patches is applied, please also run PatronusConfigYamlConsistencyTest to ensure the Patronus configuration is up to date.
"""

@Internal
fun buildUnifiedDiffText(fileName: String, originalLines: List<String>, revisedLines: List<String>): String {
  val patch = DiffUtils.diff(originalLines, revisedLines)
  return DiffUtils.generateUnifiedDiff(fileName, fileName, originalLines, patch, 3).joinToString(separator = "\n")
}

@Internal
data class ParsedContentReport(
  @JvmField val platform: List<FileEntry>,
  @JvmField val productModules: List<PluginContentReport>,
  @JvmField val bundled: List<PluginContentReport>,
  @JvmField val nonBundled: List<PluginContentReport>,
)

@Internal
fun readContentReportZip(reportFile: Path): ParsedContentReport {
  HashMapZipFile.load(reportFile).use { zip ->
    fun readEntry(name: String): String {
      return Charsets.UTF_8.decode(requireNotNull(zip.getByteBuffer(name)) { "Cannot find $name in $reportFile" }).toString()
    }

    fun readPlatformEntries(name: String): List<FileEntry> {
      val data = readEntry(name)
      try {
        return deserializeContentData(data)
      }
      catch (e: SerializationException) {
        throw RuntimeException("Cannot parse $name in $reportFile\ndata:$data", e)
      }
    }

    fun readPluginEntries(name: String): List<PluginContentReport> {
      val data = readEntry(name)
      try {
        return deserializePluginData(data)
      }
      catch (e: SerializationException) {
        throw RuntimeException("Cannot parse $name in $reportFile\ndata:$data", e)
      }
    }

    return ParsedContentReport(
      platform = readPlatformEntries("platform.yaml"),
      productModules = readPluginEntries("product-modules.yaml"),
      bundled = readPluginEntries("bundled-plugins.yaml"),
      nonBundled = readPluginEntries("non-bundled-plugins.yaml"),
    )
  }
}

@Internal
data class PackagingCheckFailure(
  @JvmField val name: String,
  @JvmField val error: Throwable,
)

/**
 * The checks over the plugins and the product modules a build reported.
 *
 * A product module is compared against the `module-content.yaml` checked in beside it.
 *
 * A plugin has no checked-in snapshot, so a plugin gets one check. The content the build reports for a bundled plugin
 * must equal the content it reports for the non-bundled build of the same plugin. This still finds a layout that
 * differs between the two builds. It no longer finds a change of one plugin's own content, and it asks no reviewer to
 * approve such a change.
 *
 * A plugin the build reports only as non-bundled therefore gets no check, and no test name is stated for one.
 */
@Internal
fun collectPluginContentFailures(
  content: ParsedContentReport,
  project: JpsProject,
  projectHome: Path,
  checkPlugins: Boolean = true,
  suggestedReviewer: String? = null,
  testName: (category: String, key: String) -> String,
): List<PackagingCheckFailure> {
  return buildList {
    addAll(
      collectProductModuleContentFailures(
        reports = toPluginContentMap(content.productModules).values,
        project = project,
        projectHome = projectHome,
        suggestedReviewer = suggestedReviewer,
        testName = { key -> testName("product-module", key) },
      )
    )

    if (!checkPlugins) {
      return@buildList
    }

    addAll(
      collectBundledPluginContentFailures(
        bundled = toPluginContentMap(content.bundled).values,
        nonBundled = toPluginContentMap(content.nonBundled),
        testName = { key -> testName("bundled-plugin", key) },
      )
    )
  }
}

/** Compares each product module against the `module-content.yaml` beside its own module. */
private fun collectProductModuleContentFailures(
  reports: Collection<PluginContentReport>,
  project: JpsProject,
  projectHome: Path,
  suggestedReviewer: String?,
  testName: (key: String) -> String,
): List<PackagingCheckFailure> {
  val failures = ArrayList<PackagingCheckFailure>()
  for ((mainModule, items) in reports.groupBy { it.mainModule }) {
    val module = project.findModuleByName(mainModule) ?: continue
    val key = getPluginContentKey(items.first())
    try {
      val contentRoot = Path.of(JpsPathUtil.urlToPath(module.contentRootsList.urls.first()))
      checkThatContentIsNotChanged(
        actualFileEntries = mergePerOsPluginContent(items),
        expectedFile = contentRoot.resolve("module-content.yaml"),
        projectHome = projectHome,
        isBundled = false,
        suggestedReviewer = suggestedReviewer,
      )
    }
    catch (t: Throwable) {
      failures.add(PackagingCheckFailure(name = testName(key), error = t))
    }
  }
  return failures
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

private fun buildDistributionChangedMessage(
  fileName: String,
  expectedLines: List<String>,
  actualLines: List<String>,
  suggestedReviewer: String?,
  requiresApproval: Boolean,
): String {
  val patchText = buildUnifiedDiffText(fileName, expectedLines, actualLines)

  return if (requiresApproval && suggestedReviewer != null) {
    """Distribution content has changed.
If you are sure that the difference is as expected, ask $suggestedReviewer to approve changes.

Please do not push changes without approval.
For more details, please visit https://youtrack.jetbrains.com/articles/IDEA-A-80/Distribution-Content-Approving.

$ADDITIONAL_INSTRUCTIONS
Patch:
$patchText"""
  }
  else {
    """Distribution content has changed.
If you are sure that the difference is as expected, please apply and commit a new snapshot.
Approval is not required. For more details, please visit https://youtrack.jetbrains.com/articles/IDEA-A-80/Distribution-Content-Approving.

Please copy the patch below and apply it, or open the Diff Viewer to accept the proposed changes.

$ADDITIONAL_INSTRUCTIONS
Patch:
$patchText"""
  }
}

/**
 * Compares the content a build produced against a checked-in snapshot, and fails with a patch when the two differ.
 *
 * Two production callers remain. One is the per-product platform snapshot, and it passes `isBundled = true`, so the
 * review path that [suggestedReviewer] drives still runs for it. The other is the `module-content.yaml` of a product
 * module, and it passes `isBundled = false`. No caller compares one plugin against a snapshot any more, so a change of
 * one plugin's own content needs no approval.
 */
@Internal
fun checkThatContentIsNotChanged(
  actualFileEntries: List<FileEntry>,
  expectedFile: Path,
  projectHome: Path,
  writeFull: Boolean = false,
  isBundled: Boolean,
  suggestedReviewer: String? = null,
) {
  val expected = try {
    deserializeContentData(Files.readString(expectedFile))
  }
  catch (_: SerializationException) {
    emptyList()
  }
  catch (_: NoSuchFileException) {
    Files.createFile(expectedFile)
    emptyList()
  }

  if (writeFull && System.getenv("TEAMCITY_VERSION") == null) {
    val actualFull = normalizeContentReport(fileEntries = actualFileEntries, short = false)
    Files.writeString(expectedFile.parent.resolve(expectedFile.fileName.toString().replace(".yaml", "-full.yaml")),
                      serializeContentEntries(actualFull))
  }

  val actual = normalizeContentReport(fileEntries = actualFileEntries, short = true)
  if (actual == expected) {
    return
  }

  val isReviewRequired = suggestedReviewer != null && isBundled && (actual.size != expected.size || !actual.asSequence().zip(expected.asSequence()).all {
    it.first.compareImportantFields(it.second)
  })

  val expectedString = serializeContentEntries(expected)
  val actualString = serializeContentEntries(actual)

  val fileName = projectHome.relativize(expectedFile).toString()

  val resultMessage = buildDistributionChangedMessage(
    fileName = fileName,
    expectedLines = expectedString.lines(),
    actualLines = actualString.lines(),
    suggestedReviewer = suggestedReviewer,
    requiresApproval = isReviewRequired,
  )

  throw FileComparisonFailedError(message = resultMessage, expected = expectedString, actual = actualString, expectedFilePath = expectedFile.toString())
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
