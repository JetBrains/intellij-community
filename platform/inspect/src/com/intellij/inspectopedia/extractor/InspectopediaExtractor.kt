// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.inspectopedia.extractor

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.codeInspection.ex.InspectionMetaInformationService
import com.intellij.codeInspection.options.*
import com.intellij.ide.plugins.PluginManagerCore.getPluginSet
import com.intellij.inspectopedia.extractor.data.Inspection
import com.intellij.inspectopedia.extractor.data.OptionsPanelInfo
import com.intellij.inspectopedia.extractor.data.Plugin
import com.intellij.inspectopedia.extractor.utils.HtmlUtils
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.system.exitProcess

private class InspectopediaExtractor : ModernApplicationStarter() {
  override suspend fun start(args: List<String>) {
    val size = args.size
    if (size != 2) {
      LOG.error("Usage: inspectopedia-generator <output directory>")
      exitProcess(-1)
    }

    val appInfo = ApplicationInfo.getInstance()
    val ideCode = appInfo.build.productCode.lowercase(Locale.getDefault())
    val ideName = appInfo.versionName
    val ideVersion = appInfo.shortVersion

    val outputDirectory = args[1]
    val rootOutputPath = Path.of(outputDirectory).toAbsolutePath()
    val outputPath = rootOutputPath.resolve(ideCode)

    try {
      Files.createDirectories(outputPath)
    }
    catch (e: IOException) {
      LOG.error("Output directory does not exist and could not be created")
      exitProcess(-1)
    }

    if (!Files.exists(outputPath) || !Files.isDirectory(outputPath) || !Files.isWritable(outputPath)) {
      LOG.error("Output path is invalid")
      exitProcess(-1)
    }

    try {
      val project = serviceAsync<ProjectManager>().defaultProject

      LOG.info("Using project ${project.name}, default: ${project.isDefault}")
      val inspectionManager = project.serviceAsync<InspectionProjectProfileManager>()
      val scopeToolStates = inspectionManager.currentProfile.allTools

      val availablePlugins = getPluginSet().allPlugins.asSequence()
        .map { Plugin(it.pluginId.idString, it.name, it.version) }
        .distinct()
        .associateByTo(HashMap()) { it.id }

      availablePlugins.put(ideName, Plugin(ideName, ideName, ideVersion))

      val inspectionsExtraState = serviceAsync<InspectionMetaInformationService>().getState()

      for (scopeToolState in scopeToolStates) {
        val wrapper = scopeToolState.tool
        val extension = wrapper.extension
        val pluginId = extension?.pluginDescriptor?.pluginId?.idString ?: ideName
        val description = wrapper.loadDescription()?.splitToSequence("<!-- tooltip end -->")?.map { it.trim() }?.filter { it.isEmpty() }?.toList()
                          ?: emptyList()

        var panelInfo: List<OptionsPanelInfo?>? = null
        try {
          val tool = wrapper.tool
          val panel = tool.optionsPane
          if (panel != OptPane.EMPTY) {
            LOG.info("Saving options panel for ${wrapper.shortName}")
            panelInfo = panel.components.map { retrievePanelStructure(it, tool.optionController) }
          }
        }
        catch (e: Throwable) {
          LOG.info("Cannot create options panel ${wrapper.shortName}", e)
        }
        val metaInformation = inspectionsExtraState.inspections.get(wrapper.id)
        val cweIds = metaInformation?.cweIds

        val language = wrapper.language
        val briefDescription = description.firstOrNull()?.let { HtmlUtils.cleanupHtml(it, language) } ?: ""
        val extendedDescription = if (description.size > 1) HtmlUtils.cleanupHtml(description[1], language) else null
        val inspection = Inspection(wrapper.shortName, wrapper.displayName, wrapper.defaultLevel.name,
                                    language, briefDescription,
                                    extendedDescription, wrapper.groupPath.asList(), wrapper.applyToDialects(),
                                    wrapper.isCleanupTool, wrapper.isEnabledByDefault, panelInfo, cweIds)

        availablePlugins.get(pluginId)!!.addInspection(inspection)
      }

      val sortedPlugins = availablePlugins.values
        .sortedBy { it.getId() }
        .onEach { it.inspections.sort() }
      val pluginData = Plugins(plugins = sortedPlugins, ideCode = ideCode, ideName = ideName, ideVersion = ideVersion)

      val jsonMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build()

      val data = try {
        jsonMapper.writeValueAsString(pluginData)
      }
      catch (e: JsonProcessingException) {
        LOG.error("Cannot serialize", e)
        exitProcess(-1)
      }

      val outPath = outputPath.resolve("$ideCode-inspections.json")
      try {
        Files.writeString(outPath, data)
      }
      catch (e: IOException) {
        LOG.error("Cannot write $outPath", e)
        exitProcess(-1)
      }
      LOG.info("Inspections info saved in $outPath")
    }
    catch (e: Exception) {
      e.printStackTrace()
      exitProcess(-1)
    }
    exitProcess(0)
  }
}

private val LOG = logger<InspectopediaExtractor>()

private fun getMyText(cmp: OptComponent): LocMessage? {
  return when (cmp) {
    is OptCheckbox -> cmp.label
    is OptString -> cmp.splitLabel
    is OptNumber -> cmp.splitLabel
    is OptExpandableString -> cmp.label
    is OptStringList -> cmp.label
    is OptTable -> cmp.label
    is OptTableColumn -> cmp.name
    is OptTab -> cmp.label
    is OptDropdown -> cmp.splitLabel
    is OptGroup -> cmp.label
    is OptSettingLink -> cmp.displayName
    else -> null
  }
}

private fun retrievePanelStructure(component: OptComponent, controller: OptionController): OptionsPanelInfo {
  val result = OptionsPanelInfo()
  result.type = component.javaClass.simpleName
  result.value = if (component is OptControl) controller.getOption(component.bindId()) else null
  if (component is OptDropdown) {
    if (result.value != null) {
      val option = component.findOption(result.value)
      result.value = option?.label?.label()
    }
    result.content = component.options.map { it.label.label() }
  }
  val text = getMyText(component)
  result.text = text?.label()
  if (component is OptDescribedComponent) {
    val description = component.description()
    result.description = description?.toString()
  }

  val children = component.children().map { retrievePanelStructure(it, controller) }
  if (!children.isEmpty()) {
    result.children = children
  }
  return result
}

@Suppress("unused")
data class Plugins(
  @JvmField val plugins: List<Plugin>,
  @JvmField val ideCode: String,
  @JvmField val ideName: String,
  @JvmField val ideVersion: String,
)
