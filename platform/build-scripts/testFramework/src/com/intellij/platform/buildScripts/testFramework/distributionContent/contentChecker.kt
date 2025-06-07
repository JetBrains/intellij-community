// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.testFramework.core.FileComparisonFailedError
import kotlinx.serialization.SerializationException
import org.assertj.core.util.diff.DiffUtils
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@ApiStatus.Internal
fun checkThatContentIsNotChanged(
  actualFileEntries: List<FileEntry>,
  expectedFile: Path,
  projectHome: Path,
  writeFull: Boolean = false,
  isBundled: Boolean,
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

  val isReviewRequired = isBundled && (actual.size != expected.size || !actual.asSequence().zip(expected.asSequence()).all {
    it.first.compareImportantFields(it.second)
  })

  val expectedString = serializeContentEntries(expected)
  val actualString = serializeContentEntries(actual)

  val expectedLines = expectedString.lines()
  val patch = DiffUtils.diff(expectedLines, actualString.lines())

  val fileName = projectHome.relativize(expectedFile).toString()

  @Suppress("SpellCheckingInspection")
  val resultMessage = if (isReviewRequired) {
    "Distribution content has changed.\n" +
    "If you are sure that the difference is as expected, ask Vladimir Krivosheev (slack://user?team=T0288D531&id=U03F6KHPW)\n" +
    "or someone else from the Core team to approve changes.\n\n" +
    "Please do not push changes without approval.\n" +
    "For more details, please visit https://youtrack.jetbrains.com/articles/IDEA-A-80/Distribution-Content-Approving.\n\n" +
    "Patch:\n${DiffUtils.generateUnifiedDiff(fileName, fileName, expectedLines, patch, 3).joinToString(separator = "\n")}"
  }
  else {
    "Distribution content has changed.\n" +
    "If you are sure that the difference is as expected, please apply and commit a new snapshot.\n" +
    "Approval is not required. For more details, please visit https://youtrack.jetbrains.com/articles/IDEA-A-80/Distribution-Content-Approving.\n\n" +
    "Please copy the patch below and apply it, or open the Diff Viewer to accept the proposed changes.\n\n" +
    "Patch:\n${DiffUtils.generateUnifiedDiff(fileName, fileName, expectedLines, patch, 3).joinToString(separator = "\n")}"
  }

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