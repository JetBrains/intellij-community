// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions", "GrazieInspection")

package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.ModuleEntry
import com.intellij.platform.distributionContent.testFramework.PluginContentReport
import com.intellij.platform.distributionContent.testFramework.deserializeContentData
import com.intellij.platform.distributionContent.testFramework.deserializeModuleList
import com.intellij.platform.distributionContent.testFramework.deserializePluginData
import com.intellij.platform.distributionContent.testFramework.serializeContentEntries
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.util.lang.HashMapZipFile
import kotlinx.serialization.SerializationException
import org.assertj.core.util.diff.DiffUtils
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.util.JpsPathUtil
import org.opentest4j.MultipleFailuresError
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
  @JvmField val moduleSets: Map<String, List<String>>,
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

    val moduleSets = zip.entries
      .asSequence()
      .filter { it.name.startsWith("moduleSets/") && it.name.endsWith(".yaml") }
      .associate { entry ->
        val moduleSetName = entry.name.removePrefix("moduleSets/").removeSuffix(".yaml")
        val yamlData = entry.getData(zip).decodeToString()
        moduleSetName to try {
          deserializeModuleList(yamlData)
        }
        catch (e: SerializationException) {
          throw RuntimeException("Cannot parse module set $moduleSetName in $reportFile\ndata:$yamlData", e)
        }
      }

    return ParsedContentReport(
      platform = readPlatformEntries("platform.yaml"),
      productModules = readPluginEntries("product-modules.yaml"),
      bundled = readPluginEntries("bundled-plugins.yaml"),
      nonBundled = readPluginEntries("non-bundled-plugins.yaml"),
      moduleSets = moduleSets,
    )
  }
}

@Internal
data class PackagingCheckFailure(
  @JvmField val name: String,
  @JvmField val error: Throwable,
)

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
      collectPluginContentCategoryFailures(
        fileEntries = toPluginContentMap(content.productModules).values.asSequence(),
        project = project,
        projectHome = projectHome,
        nonBundled = null,
        suggestedReviewer = suggestedReviewer,
        contentFileName = "module-content.yaml",
        testName = { key -> testName("product-module", key) },
      )
    )

    if (!checkPlugins) {
      return@buildList
    }

    val bundled = toPluginContentMap(content.bundled)
    val nonBundled = toPluginContentMap(content.nonBundled)
    addAll(
      collectPluginContentCategoryFailures(
        fileEntries = bundled.values.asSequence(),
        project = project,
        projectHome = projectHome,
        nonBundled = nonBundled,
        suggestedReviewer = suggestedReviewer,
        contentFileName = "plugin-content.yaml",
        testName = { key -> testName("bundled-plugin", key) },
      )
    )
    addAll(
      collectPluginContentCategoryFailures(
        fileEntries = nonBundled.values.asSequence().filter { !bundled.containsKey(getPluginContentKey(it)) },
        project = project,
        projectHome = projectHome,
        nonBundled = null,
        suggestedReviewer = suggestedReviewer,
        contentFileName = "plugin-content.yaml",
        testName = { key -> testName("non-bundled-plugin", key) },
      )
    )
  }
}

@Internal
fun assertNoPackagingCheckFailures(problemMessage: String, failures: List<PackagingCheckFailure>) {
  when (failures.size) {
    0 -> return
    1 -> throw failures.single().error
    else -> throw MultipleFailuresError(problemMessage, failures.map { wrapFailureWithName(it) })
  }
}

private fun collectPluginContentCategoryFailures(
  fileEntries: Sequence<PluginContentReport>,
  project: JpsProject,
  projectHome: Path,
  nonBundled: Map<String, PluginContentReport>?,
  suggestedReviewer: String?,
  contentFileName: String,
  testName: (key: String) -> String,
): List<PackagingCheckFailure> {
  val failures = ArrayList<PackagingCheckFailure>()
  for (item in fileEntries) {
    val module = project.findModuleByName(item.mainModule) ?: continue
    val contentRoot = Path.of(JpsPathUtil.urlToPath(module.contentRootsList.urls.first()))
    val expectedFile = contentRoot.resolve(contentFileName)
    val key = getPluginContentKey(item)
    try {
      checkThatContentIsNotChanged(
        actualFileEntries = item.content,
        expectedFile = expectedFile,
        projectHome = projectHome,
        isBundled = nonBundled != null,
        suggestedReviewer = suggestedReviewer,
      )

      if (nonBundled == null) {
        continue
      }

      val nonBundledVersion = nonBundled[key] ?: continue
      val bundledContent = normalizeContentReport(fileEntries = item.content, short = true)
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

private fun wrapFailureWithName(failure: PackagingCheckFailure): Throwable {
  return AssertionError(failure.name, failure.error)
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
