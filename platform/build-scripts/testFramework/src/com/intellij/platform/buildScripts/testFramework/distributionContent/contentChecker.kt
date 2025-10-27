// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions", "GrazieInspection")

package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.ModuleEntry
import com.intellij.platform.distributionContent.testFramework.deserializeContentData
import com.intellij.platform.distributionContent.testFramework.deserializeModuleList
import com.intellij.platform.distributionContent.testFramework.serializeContentEntries
import com.intellij.platform.distributionContent.testFramework.serializeModuleList
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import kotlinx.serialization.SerializationException
import org.assertj.core.util.diff.DiffUtils
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText

private const val ADDITIONAL_INSTRUCTIONS = """
Snapshots for other products may require update, please run 'All Packaging Tests' run configuration to run all packaging tests.

When the patches is applied, please also run PatronusConfigYamlConsistencyTest to ensure the Patronus configuration is up to date.
"""

private fun buildDistributionChangedMessage(
  fileName: String,
  expectedLines: List<String>,
  actualLines: List<String>,
  suggestedReviewer: String?,
  requiresApproval: Boolean,
): String {
  val patch = DiffUtils.diff(expectedLines, actualLines)
  val patchText = DiffUtils.generateUnifiedDiff(fileName, fileName, expectedLines, patch, 3).joinToString(separator = "\n")

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

@ApiStatus.Internal
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

  throw FileComparisonFailedError(resultMessage, expectedString, actualString, expectedFile.toString())
}


@ApiStatus.Internal
fun checkThatModuleListIsNotChanged(
  actual: List<String>,
  expectedFile: Path,
  projectHome: Path,
  suggestedReviewer: String? = null,
) {
  val expected = try {
    deserializeModuleList(expectedFile.readText())
  }
  catch (_: SerializationException) {
    emptyList()
  }
  catch (_: NoSuchFileException) {
    expectedFile.createParentDirectories()
    Files.writeString(expectedFile, serializeModuleList(emptyList()))
    emptyList()
  }

  if (actual == expected) {
    return
  }

  val expectedString = serializeModuleList(expected)
  val actualString = serializeModuleList(actual)

  val fileName = projectHome.relativize(expectedFile).toString()

  val resultMessage = buildDistributionChangedMessage(
    fileName = fileName,
    expectedLines = expectedString.lines(),
    actualLines = actualString.lines(),
    suggestedReviewer = suggestedReviewer,
    requiresApproval = true,
  )

  throw FileComparisonFailedError(resultMessage, expectedString, actualString, expectedFile.toString())
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