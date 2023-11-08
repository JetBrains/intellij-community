// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers.vscode.parsers

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.ide.customize.transferSettings.db.KnownPlugins
import com.intellij.ide.customize.transferSettings.models.FeatureInfo
import com.intellij.ide.customize.transferSettings.models.Settings
import com.intellij.ide.customize.transferSettings.models.UnknownFeature
import com.intellij.ide.customize.transferSettings.providers.vscode.mappings.KeymapPluginsMappings
import com.intellij.ide.customize.transferSettings.providers.vscode.mappings.PluginsMappings
import com.intellij.openapi.diagnostic.logger
import java.io.File

private val logger = logger<PluginsParser>()

class PluginsParser(private val settings: Settings) {
  companion object {
    private const val PUBLISHER = "publisher"
    private const val NAME = "name"
    private const val CONFIG_FILE = "package.json"
  }

  fun process(directory: File) {
    try {
      logger.info("Processing a directory: $directory")

      check(directory.isDirectory) { "It is not a directory: $directory" }

      processPluginsDirectory(directory)
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }

  private fun processPluginsDirectory(directory: File) {
    val content = directory.listFiles() ?: error("Invalid directory path: $directory")

    content.forEach {
      try {
        val pluginDirectory = it?.takeIf { it.isDirectory } ?: return@forEach
        val configFile = File("${pluginDirectory.path}/$CONFIG_FILE")

        processPluginConfigFile(configFile)
      }
      catch (t: Throwable) {
        logger.warn(t)
      }
    }
  }

  private fun processPluginConfigFile(file: File) {
    logger.debug("Processing a config file: $file")
    try {
      val root = ObjectMapper(JsonFactory().enable(JsonParser.Feature.ALLOW_COMMENTS)).readTree(file) as? ObjectNode
                 ?: error("Unexpected JSON data; expected: ${JsonNodeType.OBJECT}")

      processPluginId(root)
    }
    catch (t: Throwable) {
      logger.warn(t)
    }
  }

  private val addedPluginIds = mutableSetOf<FeatureInfo>()
  private fun processPluginId(root: ObjectNode) {
    val publisher = root[PUBLISHER]?.textValue() ?: return
    val name = root[NAME]?.textValue() ?: return

    val foreignPluginId = "$publisher.$name".lowercase()
    val keymapPlugin = KeymapPluginsMappings.map(foreignPluginId)
    if (keymapPlugin != null) {
      settings.keymap = keymapPlugin
    }
    val featureInfo = PluginsMappings.pluginIdMap(foreignPluginId) ?: UnknownFeature
    val originalPluginName = root["displayName"]?.textValue()

    if (originalPluginName == null && (featureInfo == KnownPlugins.DummyPlugin || featureInfo == KnownPlugins.DummyBuiltInFeature)) {
      return
    }

    if (!addedPluginIds.contains(featureInfo) || featureInfo == UnknownFeature) {
      settings.plugins[foreignPluginId] = featureInfo
      addedPluginIds.add(featureInfo)
    }
  }
}