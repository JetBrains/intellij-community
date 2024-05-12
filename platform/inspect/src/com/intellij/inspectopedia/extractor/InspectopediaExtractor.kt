// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.inspectopedia.extractor

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.intellij.codeInspection.ex.InspectionMetaInformationService
import com.intellij.codeInspection.options.*
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.inspectopedia.extractor.InspectopediaExtractor.Companion.getMyText
import com.intellij.inspectopedia.extractor.data.Inspection
import com.intellij.inspectopedia.extractor.data.OptionsPanelInfo
import com.intellij.inspectopedia.extractor.data.Plugin
import com.intellij.inspectopedia.extractor.data.Plugins
import com.intellij.inspectopedia.extractor.utils.HtmlUtils
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.util.containers.ContainerUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors

private class InspectopediaExtractor : ApplicationStarter {
  private val assets: MutableMap<String, ObjectMapper> = HashMap()

  init {
    val jsonMapper = JsonMapper.builder()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
      .build()
    assets.put("json", jsonMapper)
  }

  override fun main(args: List<String>) {
    val size = args.size
    if (size != 2) {
      LOG.error("Usage: inspectopedia-generator <output directory>")
      System.exit(-1)
    }

    val appInfo = ApplicationInfo.getInstance()
    val IDE_CODE = appInfo.build.productCode.lowercase(Locale.getDefault())
    val IDE_NAME = appInfo.versionName
    val IDE_VERSION = appInfo.shortVersion
    val ASSET_FILENAME = "$IDE_CODE-inspections."

    val outputDirectory = args[1]
    val rootOutputPath = Path.of(outputDirectory).toAbsolutePath()
    val outputPath = rootOutputPath.resolve(IDE_CODE)

    try {
      Files.createDirectories(outputPath)
    }
    catch (e: IOException) {
      LOG.error("Output directory does not exist and could not be created")
      System.exit(-1)
    }

    if (!Files.exists(outputPath) || !Files.isDirectory(outputPath) || !Files.isWritable(outputPath)) {
      LOG.error("Output path is invalid")
      System.exit(-1)
    }

    try {
      val project = ProjectManager.getInstance().defaultProject

      LOG.info("Using project " + project.name + ", default: " + project.isDefault)
      val inspectionManager = InspectionProjectProfileManager.getInstance(project)
      val scopeToolStates = inspectionManager.currentProfile.allTools

      val availablePlugins = Arrays.stream(
        PluginManager.getPlugins()).map { pluginDescriptor: IdeaPluginDescriptor ->
        Plugin(pluginDescriptor.pluginId.idString, pluginDescriptor.name,
               pluginDescriptor.version)
      }.distinct()
        .collect(Collectors.toMap(
          Function { obj: Plugin -> obj.getId() }, Function { plugin: Plugin -> plugin }))

      availablePlugins[IDE_NAME] = Plugin(IDE_NAME, IDE_NAME, IDE_VERSION)

      val service = ApplicationManager.getApplication().getService(
        InspectionMetaInformationService::class.java)

      val inspectionsExtraState = if (service == null) null else service.getState(null)

      for (scopeToolState in scopeToolStates) {
        val wrapper = scopeToolState.tool
        val extension = wrapper.extension
        val pluginId = extension?.pluginDescriptor?.pluginId?.idString ?: IDE_NAME
        val originalDescription = wrapper.loadDescription()
        val description = originalDescription?.split("<!-- tooltip end -->".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
                          ?: arrayOf("")

        var panelInfo: List<OptionsPanelInfo?>? = null
        try {
          val tool = wrapper.tool
          val panel = tool.optionsPane

          if (panel != OptPane.EMPTY) {
            LOG.info("Saving options panel for " + wrapper.shortName)
            panelInfo = retrievePanelStructure(panel, tool.optionController)
          }
        }
        catch (t: Throwable) {
          LOG.info("Cannot create options panel " + wrapper.shortName, t)
        }
        val metaInformation = inspectionsExtraState?.inspections?.get(wrapper.id)
        val cweIds = metaInformation?.cweIds

        val language = wrapper.language
        val briefDescription = HtmlUtils.cleanupHtml(description[0], language)
        val extendedDescription = if (description.size > 1) HtmlUtils.cleanupHtml(
          description[1], language)
        else null
        val inspection = Inspection(wrapper.shortName, wrapper.displayName, wrapper.defaultLevel.name,
                                    language, briefDescription,
                                    extendedDescription, Arrays.asList(*wrapper.groupPath), wrapper.applyToDialects(),
                                    wrapper.isCleanupTool, wrapper.isEnabledByDefault, panelInfo, cweIds)

        availablePlugins[pluginId]!!.addInspection(inspection)
      }

      val sortedPlugins = availablePlugins.values.stream()
        .sorted(Comparator.comparing { obj: Plugin -> obj.getId() })
        .peek { plugin: Plugin ->
          plugin.inspections.sort(null)
        }.toList()
      val pluginsData = Plugins(sortedPlugins, IDE_CODE, IDE_NAME, IDE_VERSION)

      for (ext in assets.keys) {
        var data: String? = ""
        try {
          data = assets[ext]!!.writeValueAsString(pluginsData)
        }
        catch (e: JsonProcessingException) {
          LOG.error("Cannot serialize " + ext.uppercase(Locale.getDefault()), e)
          System.exit(-1)
        }

        val outPath = outputPath.resolve(ASSET_FILENAME + ext)

        try {
          Files.writeString(outPath, data)
        }
        catch (e: IOException) {
          LOG.error("Cannot write $outPath", e)
          System.exit(-1)
        }
        LOG.info("Inspections info saved in $outPath")
      }
    }
    catch (e: Exception) {
      LOG.error(e.message, e)
      System.exit(-1)
    }
    System.exit(0)
  }
}

private val LOG = logger<InspectopediaExtractor>()

private fun getMyText(cmp: OptComponent): LocMessage? {
  return if (cmp is OptCheckbox) {
    cmp.label
  }
  else if (cmp is OptString) {
    cmp.splitLabel
  }
  else if (cmp is OptNumber) {
    cmp.splitLabel
  }
  else if (cmp is OptExpandableString) {
    cmp.label
  }
  else if (cmp is OptStringList) {
    cmp.label
  }
  else if (cmp is OptTable) {
    cmp.label
  }
  else if (cmp is OptTableColumn) {
    cmp.name
  }
  else if (cmp is OptTab) {
    cmp.label
  }
  else if (cmp is OptDropdown) {
    cmp.splitLabel
  }
  else if (cmp is OptGroup) {
    cmp.label
  }
  else if (cmp is OptSettingLink) {
    cmp.displayName
  }
  else {
    null
  }
}

private fun retrievePanelStructure(pane: OptPane,
                                   controller: OptionController): List<OptionsPanelInfo?> {
  val children: MutableList<OptionsPanelInfo?> = ArrayList()
  for (component in pane.components) {
    children.add(retrievePanelStructure(component, controller))
  }
  return children
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
    result.content = ContainerUtil.map(component.options) { opt: OptDropdown.Option -> opt.label.label() }
  }
  val text = getMyText(component)
  result.text = text?.label()
  if (component is OptDescribedComponent) {
    val description = component.description()
    result.description = description?.toString()
  }
  val children: MutableList<OptionsPanelInfo> = ArrayList()
  for (child in component.children()) {
    children.add(retrievePanelStructure(child, controller))
  }
  if (!children.isEmpty()) {
    result.children = children
  }
  return result
}