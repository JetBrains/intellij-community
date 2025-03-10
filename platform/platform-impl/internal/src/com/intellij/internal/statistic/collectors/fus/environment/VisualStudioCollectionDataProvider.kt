// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.environment

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private val logger = logger<VisualStudioCollectionDataProvider>()

/**
 * Collects anonymous data about the presense of Visual Studio to improve the "Import settings from external editor" feature and prioritize
 * support for popular editors.
 *
 * This includes:
 * - detecting if Visual Studio is installed on Windows via %LOCALAPPDATA%,
 * - detecting if Visual Studio is installed on Windows via registry (legacy detection).
 *
 * The data is completely anonymized and no personally identifiable information is captured.
 */
internal class VisualStudioCollectionDataProvider : ExternalEditorCollectionDataProvider() {

  suspend fun getInstalledVersions(): List<String> {
    if (!SystemInfo.isWindows) return emptyList()
    return (readVersionsFromAppData() + readVersionsFromRegistry()).distinct().sorted().toList()
  }

  private data class VsVersion(val major: Int, val minor: Int, val instanceId: String?) {
    override fun toString(): String {
      return "$major.$minor"
    }
  }
  // Example: "15.0_a0848a47Exp", where VS version = "15.0", Instance Id = "a0848a47", Root Suffix = "Exp".
  private val vsVersionWithHiveRegex = Regex("\\b([0-9]{1,2})\\.([0-9]{1,2})(?:_([a-fA-F0-9]{8}))?[a-zA-Z0-9]*\\b")
  private fun matchVersion(hive: String): VsVersion? {
    val match = vsVersionWithHiveRegex.find(hive) ?: return null
    return VsVersion(
      major = match.groupValues[1].toIntOrNull() ?: return null,
      minor = match.groupValues[2].toIntOrNull() ?: return null,
      instanceId = match.groupValues.elementAtOrNull(3)
    )
  }

  private suspend fun readVersionsFromAppData(): Sequence<String> =
    withContext(Dispatchers.IO) {
      logger.runAndLogException {
        val appData = System.getenv("LOCALAPPDATA") ?: return@withContext emptySequence()
        val visualStudioHomeDir = Path(appData, "Microsoft", "VisualStudio")
        if (!visualStudioHomeDir.isDirectory())
          return@withContext emptySequence()

        visualStudioHomeDir.listDirectoryEntries().asSequence()
          .mapNotNull { matchVersion(it.name) }
          .filter { it.instanceId?.length == 8 }
          .map { it.toString() }
      } ?: emptySequence()
    }

  private fun readVersionsFromRegistry(): Sequence<String> = logger.runAndLogException {
    val key = "SOFTWARE\\Microsoft\\VisualStudio"
    val registry = if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) {
      Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, key)
    }
    else {
      return emptySequence()
    }

    registry.asSequence()
      .mapNotNull { matchVersion(it) }
      .filter { it.major == 11 || it.major == 12 || it.major == 14 }
      .map { it.toString() }
  } ?: emptySequence()
}
