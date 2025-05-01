// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.environment

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private const val VIMRC_ID = ".vimrc"
private const val VSCODE_ID = ".vscode"
private const val CURSOR_ID = ".cursor"
private const val WINDSURF_ID = ".windsurf"
private const val TRAE_ID = ".trae"
private const val ECLIPSE_ID = ".eclipse"
private const val ZED_ID = ".zed"
private const val VISUAL_STUDIO_ID = "VisualStudio"

private const val NONE = "none"

internal class EditorsCollector : ApplicationUsagesCollector() {
  private val EDITORS_GROUP: EventLogGroup = EventLogGroup("editors", 9)

  override fun getGroup(): EventLogGroup = EDITORS_GROUP

  private val CONFIGS: List<String> = listOf(
    NONE,
    VIMRC_ID,
    VSCODE_ID,
    CURSOR_ID,
    WINDSURF_ID,
    TRAE_ID,
    ECLIPSE_ID,
    ZED_ID,
    VISUAL_STUDIO_ID
  )

  private val CONFIG_EXISTS: EventId1<String> = EDITORS_GROUP.registerEvent(
    "config.exists",
    EventFields.String("config", CONFIGS)
  )

  private val IS_VSCODE_USED_RECENTLY: EventId1<Boolean> = EDITORS_GROUP.registerEvent(
    "vscode.used.recently",
    EventFields.Boolean("is_vscode_used_recently"))

  private val VS_CODE_EXTENSION_INSTALLED: EventId1<List<String>> = EDITORS_GROUP.registerEvent(
    "vscode.extension.installed",
    EventFields.StringList("extension_ids", emptyList())
  )

  private val VISUAL_STUDIO_VERSIONS_INSTALLED: EventId1<List<String>> = EDITORS_GROUP.registerEvent(
    "visual.studio.versions.installed",
    EventFields.StringListValidatedByRegexp("versions", "version")
  )

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    val homeDir = Paths.get(System.getProperty("user.home"))
    val codeEditorsHome = when {
      SystemInfo.isMac -> homeDir.resolve("Library").resolve("Application Support")
      SystemInfo.isWindows -> getWindowsEnvVariableValue("APPDATA").let { Paths.get(it) }
      else -> homeDir.resolve(".config")
    }

    return withContext(Dispatchers.IO) {
      buildSet {
        if (
          Files.exists(homeDir.resolve(".vimrc")) ||
          Files.exists(homeDir.resolve("_vimrc")) ||
          Files.exists(homeDir.resolve(".vim/vimrc"))
        ) {
          add(CONFIG_EXISTS.metric(VIMRC_ID))
        }

        val vsCodeCollectionDataProvider = VSCodeCollectionDataProvider()
        if (vsCodeCollectionDataProvider.isVSCodeDetected()) {
          val isVSCodeUsedRecently = vsCodeCollectionDataProvider.isVSCodeUsedRecently()
          isVSCodeUsedRecently?.let {
            add(IS_VSCODE_USED_RECENTLY.metric(it))
          }

          if (vsCodeCollectionDataProvider.isVSCodePluginsProcessingPossible()) {
            add(VS_CODE_EXTENSION_INSTALLED.metric(vsCodeCollectionDataProvider.getVSCodePluginsIds()))
          }
          add(CONFIG_EXISTS.metric(VSCODE_ID))
        }

        if (Files.isDirectory(codeEditorsHome.resolve("Cursor"))) {
          add(CONFIG_EXISTS.metric(CURSOR_ID))
        }

        if (Files.isDirectory(codeEditorsHome.resolve("Windsurf"))) {
          add(CONFIG_EXISTS.metric(WINDSURF_ID))
        }

        if (Files.isDirectory(codeEditorsHome.resolve("Trae"))) {
          add(CONFIG_EXISTS.metric(TRAE_ID))
        }

        if (Files.isDirectory(homeDir.resolve(".eclipse"))) {
          add(CONFIG_EXISTS.metric(ECLIPSE_ID))
        }

        val zedCollectionDataProvider = ZedCollectionDataProvider()
        if (zedCollectionDataProvider.isZedDetected()) {
          add(CONFIG_EXISTS.metric(ZED_ID))
        }

        val vsVersions = VisualStudioCollectionDataProvider().getInstalledVersions()
        if (vsVersions.any()) {
          add(CONFIG_EXISTS.metric(VISUAL_STUDIO_ID))
          add(VISUAL_STUDIO_VERSIONS_INSTALLED.metric(vsVersions))
        }

        if (isEmpty()) {
          add(CONFIG_EXISTS.metric(NONE))
        }
      }
    }
  }
}

/**
 * Function to get one Windows env var.
 * Use without %%, example: `get("APPDATA")`
 *
 * @return env var value or "null" string if not found
 */
internal fun getWindowsEnvVariableValue(variable: String): String {
  val envMap = try {
    System.getenv()
  }
  catch (e: SecurityException) {
    fileLogger().debug(e)
    return "null"
  }

  val varValue = envMap[variable] ?: envMap[variable.uppercase(Locale.getDefault())].toString()
  fileLogger().debug { "Detected system env var value - $variable: $varValue" }
  return varValue
}