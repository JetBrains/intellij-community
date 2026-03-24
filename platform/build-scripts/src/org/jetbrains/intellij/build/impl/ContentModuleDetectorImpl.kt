// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleDetector
import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleRegistrationData
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import org.jdom.Element
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.impl.projectStructureMapping.ContentReport
import org.jetbrains.jps.model.module.JpsModule
import kotlin.io.path.pathString

/**
 * Provide information about [JpsModule] registered as content modules using data [DescriptorCacheContainer].
 */
internal class ContentModuleDetectorImpl(platformLayout: PlatformLayout, contentReport: ContentReport) : ContentModuleDetector {
  private val contentModules = mutableMapOf<String, ContentModuleRegistrationData>()

  init {
    val platformContainer = platformLayout.descriptorCacheContainer.forPlatform(platformLayout)
    val corePluginContent = platformContainer.getCachedFileData(PRODUCT_DESCRIPTOR_META_PATH) ?: error("Cannot find core plugin descriptor")
    collectContentModules(corePluginContent, platformContainer, presentablePluginDescription = "the core plugin")
    contentReport.bundledPlugins.forEach { plugin ->
      val descriptorContainer = platformLayout.descriptorCacheContainer.forPlugin(plugin.dir)
      val fileContent = descriptorContainer.getCachedFileData(PLUGIN_XML_RELATIVE_PATH)
                        ?: error("Cannot find plugin.xml for ${plugin.dir} in the cache")
      collectContentModules(fileContent, descriptorContainer, presentablePluginDescription = plugin.dir.pathString)
    }
  }

  private fun collectContentModules(
    fileContent: ByteArray,
    descriptorContainer: ScopedCachedDescriptorContainer,
    presentablePluginDescription: String,
  ) {
    val rootTag = JDOMUtil.load(fileContent)
    val pluginId = rootTag.getChildText("id")
    rootTag.getChildren("content").forEach { contentTag ->
      val namespace = contentTag.getAttributeValue("namespace") ?: $$"$${pluginId ?: error("<id> tag is not set in plugin.xml in $presentablePluginDescription")}_$implicit"
      contentTag.getChildren("module").forEach {
        val moduleName = it.getAttributeValue("name") ?: error("'name' attribute is missing for <module> tag in plugin.xml in $presentablePluginDescription")
        if (moduleName.contains("/")) return@forEach //todo remove this check after all content modules are extracted to separate JPS modules (IJPL-165543)

        val moduleXmlData = descriptorContainer.getCachedFileData("$moduleName.xml")
                            ?: error("Cannot find $moduleName.xml descriptor for $presentablePluginDescription")
        val moduleXmlRoot = JDOMUtil.load(moduleXmlData)
        val visibility = parseVisibility(moduleXmlRoot)
        contentModules[moduleName] = ContentModuleRegistrationData(moduleName, namespace, visibility)
      }
    }
  }

  override fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationData? {
    return contentModules[jpsModule.name]
  }
}

private fun parseVisibility(moduleXmlRoot: Element): RuntimeModuleVisibility {
  val visibilityString = moduleXmlRoot.getAttributeValue("visibility")
  return when (visibilityString) {
    "public" -> RuntimeModuleVisibility.PUBLIC
    "internal" -> RuntimeModuleVisibility.INTERNAL
    "private" -> RuntimeModuleVisibility.PRIVATE
    else -> RuntimeModuleVisibility.PRIVATE
  }
}
