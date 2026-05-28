// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleDetector
import com.intellij.devkit.runtimeModuleRepository.generator.ContentModuleRegistrationData
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.runtime.repository.IncludedRuntimeModule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.repository.impl.RuntimePluginHeaderImpl
import org.jdom.Element
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.impl.projectStructureMapping.CustomAssetEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleLibraryFileEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ModuleTestOutputEntry
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectLibraryEntry
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import kotlin.io.path.pathString

/**
 * Provide information about [JpsModule] registered as content modules using data [DescriptorCacheContainer].
 */
internal class ContentModuleDetectorImpl(
  platformLayout: PlatformLayout,
  corePluginDescriptorModuleName: String,
  platformEntries: List<DistributionFileEntry>,
  bundledPlugins: List<PluginBuildDescriptor>,
  embeddedFrontendDescriptorModuleName: String?,
  project: JpsProject,
) : ContentModuleDetector {
  private data class ContentModuleInPlugin(val pluginId: String, val moduleName: String)
  private val contentModules = mutableMapOf<String, ContentModuleRegistrationData>()
  private val loadingRulesForContentModules = mutableMapOf<ContentModuleInPlugin, RuntimeModuleLoadingRule>()
  private val requiredIfAvailableAttributeForContentModules = mutableMapOf<ContentModuleInPlugin, RuntimeModuleId>()
  val pluginHeaders: List<RuntimePluginHeader>

  init {
    val platformContainer = platformLayout.descriptorCacheContainer.forPlatform(platformLayout)
    val corePluginContent = platformContainer.getCachedFileData(PRODUCT_DESCRIPTOR_META_PATH) ?: error("Cannot find core plugin descriptor")
    processPluginDescriptor(corePluginContent, platformContainer, emptyList(), presentablePluginDescription = "the core plugin")
    val pluginDescriptorModuleNameToId = HashMap<String, String>()

    val additionalContainersForEmbeddedFrontend =
      if (embeddedFrontendDescriptorModuleName != null) {
        //descriptors for embedded frontend may be stored inside containers for the platform, and for the corresponding plugin, see deprecatedResolveDescriptorForEmbeddedProduct
        val pluginWithEmbeddedFrontend = bundledPlugins.find { plugin ->
          plugin.layout.includedModules.any { it.moduleName == embeddedFrontendDescriptorModuleName } && plugin.layout.includedModules.size > 1
        } ?: error("Cannot find plugin with embedded frontend $embeddedFrontendDescriptorModuleName")
        listOf(platformContainer, platformLayout.descriptorCacheContainer.forPlugin(pluginWithEmbeddedFrontend.dir))
      }
      else emptyList()

    bundledPlugins.forEach { plugin ->
      val descriptorContainer = platformLayout.descriptorCacheContainer.forPlugin(plugin.dir)
      val fileContent = descriptorContainer.getCachedFileData(PLUGIN_XML_RELATIVE_PATH)
                        ?: error("Cannot find plugin.xml for ${plugin.dir} in the cache")
      val pluginId = processPluginDescriptor(fileContent, descriptorContainer, additionalContainersForEmbeddedFrontend, presentablePluginDescription = plugin.dir.pathString)
      pluginDescriptorModuleNameToId[plugin.layout.mainModule] = pluginId
    }
    val corePluginModuleId = createModuleId(corePluginDescriptorModuleName, project)
    val corePluginHeader = createCorePluginHeader(platformEntries, corePluginModuleId, project)
    pluginHeaders = listOf(corePluginHeader) + bundledPlugins.map { plugin ->
      createPluginHeader(plugin, pluginId = pluginDescriptorModuleNameToId.getValue(plugin.layout.mainModule), project = project)
    }
  }

  private fun processPluginDescriptor(
    fileContent: ByteArray,
    descriptorContainer: ScopedCachedDescriptorContainer,
    additionalContainersForEmbeddedFrontend: List<ScopedCachedDescriptorContainer>,
    presentablePluginDescription: String,
  ): String {
    val rootTag = JDOMUtil.load(fileContent)
    val pluginId = rootTag.getChildText("id") ?: error("<id> tag is not set in plugin.xml for $presentablePluginDescription")
    rootTag.getChildren("content").forEach { contentTag ->
      val namespace = contentTag.getAttributeValue("namespace") ?: $$"$${pluginId}_$implicit"
      contentTag.getChildren("module").forEach { moduleTag ->
        val moduleName = moduleTag.getAttributeValue("name") ?: error("'name' attribute is missing for <module> tag in plugin.xml in $presentablePluginDescription")
        if (moduleName.contains("/")) return@forEach //todo remove this check after all content modules are extracted to separate JPS modules (IJPL-165543)

        val descriptorName = "$moduleName.xml"
        var moduleXmlData = descriptorContainer.getCachedFileData(descriptorName)
        if (moduleXmlData == null && pluginId == "com.intellij") {
          moduleXmlData = additionalContainersForEmbeddedFrontend.firstNotNullOfOrNull { it.getCachedFileData(descriptorName) }
        }
        require(moduleXmlData != null) { "Cannot find $descriptorName descriptor for $presentablePluginDescription" }
        val moduleXmlRoot = JDOMUtil.load(moduleXmlData)
        val visibility = parseVisibility(moduleXmlRoot)
        val loadingRule = parseLoadingRule(moduleTag)
        contentModules[moduleName] = ContentModuleRegistrationData(moduleName, namespace, visibility)
        val requiredIfAvailableName = moduleTag.getAttributeValue("required-if-available")
        val key = ContentModuleInPlugin(pluginId, moduleName)
        if (requiredIfAvailableName != null) {
          requiredIfAvailableAttributeForContentModules[key] = RuntimeModuleId.contentModule(requiredIfAvailableName, RuntimeModuleId.DEFAULT_NAMESPACE)
        }
        loadingRulesForContentModules[key] = loadingRule
      }
    }
    return pluginId
  }

  override fun findContentModuleData(jpsModule: JpsModule): ContentModuleRegistrationData? {
    return contentModules[jpsModule.name]
  }

  override fun findContentModuleDataForTests(jpsModule: JpsModule): ContentModuleRegistrationData? {
    val data = contentModules["${jpsModule.name}._test"]
    if (data != null) return data
    if (hasTestSourcesAndNoProductionSources(jpsModule)) {
      //some modules (e.g. intellij.rider.test.cases.common) don't use `._test` suffix
      return contentModules[jpsModule.name]
    }
    return null
  }

  private fun createPluginHeader(plugin: PluginBuildDescriptor, pluginId: String, project: JpsProject): RuntimePluginHeader {
    val pluginDescriptorModuleId = createModuleId(plugin.layout.mainModule, project)
    val includedModules = convertDistributionEntriesToIncludedModules(plugin.distribution, project, pluginId)
    return RuntimePluginHeaderImpl(pluginId, pluginDescriptorModuleId, includedModules)
  }

  private fun convertDistributionEntriesToIncludedModules(
    entries: Collection<DistributionFileEntry>,
    project: JpsProject,
    pluginId: String,
  ): List<IncludedRuntimeModule> {
    val includedModules = entries.mapNotNull { entry ->
      val relativeOutputPath = entry.relativeOutputFile ?: ""
      when (entry) {
        is ModuleOutputEntry -> {
          val moduleName = entry.owner.moduleName
          val key = ContentModuleInPlugin(pluginId, moduleName)
          val loadingRule = loadingRulesForContentModules[key] ?: RuntimeModuleLoadingRule.EMBEDDED
          val requiredIfAvailableId = requiredIfAvailableAttributeForContentModules[key]
          IncludedRuntimeModuleImpl(createModuleId(moduleName, project), loadingRule, requiredIfAvailableId)
        }
        is ProjectLibraryEntry -> {
          IncludedRuntimeModuleImpl(RuntimeModuleId.projectLibrary(entry.data.libraryName), RuntimeModuleLoadingRule.EMBEDDED, null)
        }
        is ModuleTestOutputEntry ->
          IncludedRuntimeModuleImpl(RuntimeModuleId.moduleTests(entry.moduleName), RuntimeModuleLoadingRule.EMBEDDED, null)
        is ModuleLibraryFileEntry -> null // module-level libraries are included in the runtime descriptor for corresponding module
        is CustomAssetEntry -> null
      }?.takeIf { shouldIncludeInPluginHeader(it.moduleId, relativeOutputPath) }
    }
    return includedModules
  }

  private fun createCorePluginHeader(platformEntries: List<DistributionFileEntry>, pluginDescriptorModuleId: RuntimeModuleId, project: JpsProject): RuntimePluginHeader {
    val pluginId = "com.intellij"
    val includedModules = convertDistributionEntriesToIncludedModules(platformEntries, project, pluginId)
    return RuntimePluginHeaderImpl(pluginId, pluginDescriptorModuleId, includedModules)
  }

  /**
   * Returns `true` if the module [moduleId] should be included in the plugin header: if its JAR is located directly in `lib/` directory, or the JAR is in `lib/modules/` directory,
   * and it's a real content module.
   */
  private fun shouldIncludeInPluginHeader(moduleId: RuntimeModuleId, relativeOutputPath: String): Boolean {
    if (!relativeOutputPath.contains('/')) return true
    if (!relativeOutputPath.startsWith("modules/") || relativeOutputPath.removePrefix("modules/").contains('/')) return false
    val namespace = moduleId.namespace
    return namespace != RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE && namespace != RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE
           && namespace != RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE
  }

  private fun createModuleId(moduleName: String, project: JpsProject): RuntimeModuleId {
    val pluginDescriptorModuleData = contentModules[moduleName]
    val pluginDescriptorModuleId = if (pluginDescriptorModuleData != null) {
      RuntimeModuleId.contentModule(pluginDescriptorModuleData.name, pluginDescriptorModuleData.namespace)
    }
    else {
      val module = project.findModuleByName(moduleName)
      if (module != null && hasTestSourcesAndNoProductionSources(module)) {
        return RuntimeModuleId.moduleTests(moduleName)
      }
      else {
        RuntimeModuleId.legacyJpsModule(moduleName)
      }
    }
    return pluginDescriptorModuleId
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

private fun parseLoadingRule(moduleTag: Element): RuntimeModuleLoadingRule {
  val loadingRuleString = moduleTag.getAttributeValue("loading")
  return when (loadingRuleString) {
    "required" -> RuntimeModuleLoadingRule.REQUIRED
    "optional" -> RuntimeModuleLoadingRule.OPTIONAL
    "embedded" -> RuntimeModuleLoadingRule.EMBEDDED
    "on-demand" -> RuntimeModuleLoadingRule.ON_DEMAND
    else -> RuntimeModuleLoadingRule.OPTIONAL
  }
}