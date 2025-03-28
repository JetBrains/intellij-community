// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.environment

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.openapi.util.SystemInfo.isWindows
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.io.path.*

private const val VSCODE_PLUGINS_IDENTIFICATION_TAG = "identifier"
private const val VSCODE_PLUGINS_ID_TAG = "id"
private val logger = logger<VSCodeCollectionDataProvider>()


/**
 * Collects anonymous data about installed VSCode plugins to improve the "Import settings from VSCode" feature
 * and prioritize support for popular plugins.
 *
 * This includes:
 * - Detecting if VSCode is installed and determining its configuration directories across platforms (Windows, macOS, Linux).
 * - Parsing the VSCode configuration file (`extensions.json`) to identify installed plugins.
 *   Though, only info about public popular plugins from the VSCode Marketplace is collected.
 * - Checking the last usage time of VSCode to ensure the relevance of collected data.
 *
 * The data is completely anonymized and no personally identifiable information is captured.
 * All collected data is used to enhance support "Import settings from VSCode" for widely used VSCode plugins.
 */
internal class VSCodeCollectionDataProvider : ExternalEditorCollectionDataProvider() {

  private val vsCodeHomePath: Path? = when {
    isMac -> homeDirectory?.resolve(Paths.get("Library", "Application Support", "Code"))
    isWindows -> getWindowsVSCodeHomePath()
    else -> homeDirectory?.resolve(Paths.get(".config", "Code"))
  }

  private val pluginsDirectoryPath = homeDirectory?.resolve(Paths.get(".vscode", "extensions"))
  private val pluginsConfigPath = pluginsDirectoryPath?.resolve("extensions.json")
  private val databasePath = vsCodeHomePath?.resolve(Paths.get("User", "globalStorage", "state.vscdb"))

  init {
    logger.debug { "VSCode home path: $vsCodeHomePath" }
    logger.debug { "VSCode plugins directory path: $pluginsDirectoryPath" }
    logger.debug { "VSCode plugins config path: $pluginsConfigPath" }
    logger.debug { "VSCode database path: $databasePath" }
  }

  private val maxTimeSinceLastModificationToBeRecent = Duration.ofHours(30 * 24) // 30 days

  fun isVSCodeDetected(): Boolean {
    if (homeDirectory == null) {
      logger.debug { "VSCode is not detected - home directory is null" }
      return false
    }

    val vsCodeConfigDir = homeDirectory.resolve(".vscode")

    return try {
      val isVsCodeConfigDirValid = Files.isDirectory(vsCodeConfigDir)
      logger.debug { "Is $vsCodeConfigDir a valid directory: $isVsCodeConfigDirValid" }
      isVsCodeConfigDirValid
    }
    catch (e: SecurityException) {
      logger.debug(e)
      false
    }
  }

  fun isVSCodeUsedRecently(): Boolean? {
    return try {
      if (databasePath?.exists() == true) {
        val time = Files.getLastModifiedTime(databasePath)
        val isVSCodeUsedRecently = time.toInstant() > Instant.now() - maxTimeSinceLastModificationToBeRecent
        logger.debug { "Is VSCode used recently: $isVSCodeUsedRecently" }
        return isVSCodeUsedRecently
      }

      logger.debug { "VSCode is not used recently - database path doesn't exist" }
      null
    }
    catch (e: IOException) {
      logger.debug(e)
      null
    }
    catch (e: SecurityException) {
      logger.debug(e)
      null
    }
  }

  fun isVSCodePluginsProcessingPossible(): Boolean {
    return try {
      val isVSCodePluginDirExists = pluginsDirectoryPath?.exists() == true
      val isVSCodeConfigDirValid = pluginsDirectoryPath?.isDirectory() == true
      val isVSCodeConfigFilePathExists = pluginsConfigPath?.exists() == true
      val isVSCodeConfigFilePathValid = pluginsConfigPath?.isRegularFile() == true
      val isVSCodeConfigFilePathReadable = pluginsConfigPath?.isReadable() == true

      logger.debug {
        "isVSCodePluginDirExists = $isVSCodePluginDirExists\n" +
        "isVSCodeConfigDirValid = $isVSCodeConfigDirValid\n" +
        "isVSCodeConfigFilePathExists = $isVSCodeConfigFilePathExists\n" +
        "isVSCodeConfigFilePathValid = $isVSCodeConfigFilePathValid\n" +
        "isVSCodeConfigFilePathReadable = $isVSCodeConfigFilePathReadable"
      }

      isVSCodePluginDirExists && isVSCodeConfigDirValid &&
      isVSCodeConfigFilePathExists && isVSCodeConfigFilePathValid &&
      isVSCodeConfigFilePathReadable
    }
    catch (e: SecurityException) {
      logger.debug(e)
      false
    }
  }

  fun getVSCodePluginsIds(): List<String> {
    val pluginsConfigData = pluginsConfigPath?.readText() ?: return emptyList()

    val parsedJson = try {
      Json.parseToJsonElement(pluginsConfigData)
    }
    catch (e: SerializationException) {
      logger.debug(e)
      return emptyList()
    }

    val pluginIdsList = mutableListOf<String>()

    try {
      for (pluginElement in parsedJson.jsonArray) {
        val pluginInfoObject = pluginElement.jsonObject
        pluginInfoObject[VSCODE_PLUGINS_IDENTIFICATION_TAG]?.jsonObject?.get(VSCODE_PLUGINS_ID_TAG)?.let {
          pluginIdsList.add(it.jsonPrimitive.content)
        }
      }
    }
    catch (e: IllegalArgumentException) {
      logger.debug(e)
      return pluginIdsList
    }

    logger.debug { "Detected VSCode plugins: ${pluginIdsList.joinToString(",")}}" }
    return pluginIdsList
  }

  private fun getWindowsVSCodeHomePath(): Path? {
    return try {
      val appDataValue = Paths.get(getWindowsEnvVariableValue("APPDATA"), "Code")
      logger.debug { "Detected APPDATA env var value: $appDataValue" }
      appDataValue
    }
    catch (e: InvalidPathException) {
      logger.debug(e)
      null
    }
  }
}