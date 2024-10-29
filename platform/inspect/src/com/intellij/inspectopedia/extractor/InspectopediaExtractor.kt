// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.inspectopedia.extractor

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.codeInspection.ex.InspectionMetaInformationService
import com.intellij.codeInspection.options.*
import com.intellij.ide.plugins.PluginManagerCore.getPluginSet
import com.intellij.inspectopedia.extractor.data.Inspection
import com.intellij.inspectopedia.extractor.data.OptionsPanelInfo
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    val rootOutputPath = Path.of(args[1]).toAbsolutePath().normalize()
    val outputPath = rootOutputPath.resolve(ideCode)

    try {
      withContext(Dispatchers.IO) {
        Files.createDirectories(outputPath)
      }
    }
    catch (e: IOException) {
      LOG.error("Output directory does not exist and could not be created")
      ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true, -1 )
    }

    if (!Files.isDirectory(outputPath) || !Files.isWritable(outputPath)) {
      LOG.error("Output path is invalid")
      ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true, -1 )
    }

    try {
      val project = serviceAsync<ProjectManager>().defaultProject

      LOG.info("Using project ${project.name}, default: ${project.isDefault}")
      val scopeToolStates = project.serviceAsync<InspectionProjectProfileManager>().currentProfile.allTools

      val availablePlugins = getPluginSet().allPlugins.asSequence()
        .map { Plugin(id = it.pluginId.idString, name = it.name, version = it.version) }
        .distinct()
        .associateByTo(HashMap()) { it.id }

      availablePlugins.put(ideName, Plugin(id = ideName, name = ideName, version = ideVersion))

      val inspectionExtraState = serviceAsync<InspectionMetaInformationService>().getState()

      for (scopeToolState in scopeToolStates) {
        val wrapper = scopeToolState.tool
        val extension = wrapper.extension
        val pluginId = extension?.pluginDescriptor?.pluginId?.idString ?: ideName
        val description = wrapper.loadDescription()?.split("<!-- tooltip end -->")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList()
                          ?: emptyList()

        var panelInfo: List<OptionsPanelInfo>? = null
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

        try {
          val language = wrapper.language
          val extraState = inspectionExtraState.inspections.get(wrapper.id)
          availablePlugins.get(pluginId)!!.inspections.add(Inspection(
            id = wrapper.tool.alternativeID ?: wrapper.id,
            name = wrapper.displayName,
            severity = wrapper.defaultLevel.name,
            language = language,
            briefDescription = description.firstOrNull()?.let { HtmlUtils.cleanupHtml(it, language) },
            extendedDescription = if (description.size > 1) HtmlUtils.cleanupHtml(description[1], language) else null,
            path = wrapper.groupPath.asList(),
            isAppliesToDialects = wrapper.applyToDialects(),
            isCleanup = wrapper.isCleanupTool,
            isEnabledDefault = wrapper.isEnabledByDefault,
            options = panelInfo,
            cweIds = extraState?.cweIds,
            codeQualityCategory = extraState?.codeQualityCategory,
          ))
        }
        catch (e: Throwable) {
          System.err.println("Error while processing ${wrapper.extension}")
          e.printStackTrace()
          ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true, -1 )
        }
      }

      val sortedPlugins = availablePlugins.values
        .sortedBy { it.id }
        .onEach { it.inspections.sort() }
      val pluginData = Plugins(plugins = sortedPlugins, ideCode = ideCode, ideName = ideName, ideVersion = ideVersion)

      // we cannot use kotlin serialization - `OptionsPanelInfo.value` uses `Any` type
      val jsonMapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .serializationInclusion(JsonInclude.Include.NON_DEFAULT)
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build()

      val outPath = outputPath.resolve("$ideCode-inspections.json")
      withContext(Dispatchers.IO) {
        Files.newOutputStream(outPath).use {
          jsonMapper.writeValue(it, pluginData)
        }
      }
      LOG.info("Inspections info saved in $outPath")
    }
    catch (e: Exception) {
      e.printStackTrace()
      ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true, -1 )
    }
    ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true )
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
    result.value?.let {
      result.value = component.findOption(it)?.label?.label()
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
private data class Plugins(
  @JvmField val plugins: List<Plugin>,
  @JvmField val ideCode: String,
  @JvmField val ideName: String,
  @JvmField val ideVersion: String,
)

private data class Plugin(
  @JvmField val id: String,
  @JvmField val name: String,
  @JvmField val version: String?,
) {
  @JvmField
  val inspections: MutableList<Inspection> = mutableListOf()
}