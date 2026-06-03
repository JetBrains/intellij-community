// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.parseContentAndXIncludes
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import org.jdom.Element
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.impl.PRODUCT_DESCRIPTOR_META_PATH
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer

/**
 * Represents the data from `plugin.xml` descriptor that is required to generate [com.intellij.platform.runtime.repository.RuntimePluginHeader]
 */
internal class PluginDescriptorDataForHeader(
  val pluginId: String,
  val pluginDescriptorJpsModuleName: String,
  val additionalFrontendOnlyPlugin: Boolean,
  val contentModules: Map<String, ContentModuleRegistrationDataForHeader>,
) {
  override fun toString(): String {
    return "PluginDescriptorDataForHeader{pluginId=$pluginId, pluginDescriptorJpsModuleName=$pluginDescriptorJpsModuleName, additionalFrontendOnlyPlugin=$additionalFrontendOnlyPlugin}"
  }
}

internal data class ContentModuleRegistrationDataForHeader(
  val name: String,
  val namespace: String,
  val loadingRule: RuntimeModuleLoadingRule,
  val requiredIfAvailable: RuntimeModuleId?,
  val visibility: RuntimeModuleVisibility,
)

/**
 * Fetches plugin descriptor data from descriptors of the core and bundled plugins, including additional plugins for the embedded frontend.
 * For performance reasons, it takes contents of descriptor files from [org.jetbrains.intellij.build.impl.DescriptorCacheContainer] where xi:include references are already inlined.
 */
internal fun fetchPluginDescriptorsData(
  platformLayout: PlatformLayout,
  corePluginDescriptorModuleName: String,
  embeddedFrontendDescriptorModuleName: String?,
  bundledPlugins: List<PluginBuildDescriptor>,
  additionalFrontendOnlyPlugins: List<PluginBuildDescriptor>
): List<PluginDescriptorDataForHeader> {
  val platformContainer = platformLayout.descriptorCacheContainer.forPlatform(platformLayout)
  val corePluginContent = platformContainer.getCachedFileData(PRODUCT_DESCRIPTOR_META_PATH) ?: error("Cannot find core plugin descriptor")

  val corePluginDescriptorData = fetchPluginDescriptorDataForHeader(
    corePluginContent,
    pluginDescriptorJpsModuleName = corePluginDescriptorModuleName,
    platformContainer,
    additionalContainersForEmbeddedFrontend = emptyList(),
    additionalFrontendOnlyPlugin = false,
  )

  val additionalContainersForEmbeddedFrontend =
    if (embeddedFrontendDescriptorModuleName != null) {
      //descriptors for embedded frontend may be stored inside containers for the platform, and for the corresponding plugin, see deprecatedResolveDescriptorForEmbeddedProduct
      val pluginWithEmbeddedFrontend = bundledPlugins.find { plugin ->
        plugin.layout.includedModules.any { it.moduleName == embeddedFrontendDescriptorModuleName } && plugin.layout.includedModules.size > 1
      } ?: error("Cannot find plugin with embedded frontend $embeddedFrontendDescriptorModuleName")
      listOf(platformContainer, platformLayout.descriptorCacheContainer.forPlugin(pluginWithEmbeddedFrontend.dir))
    }
    else emptyList()

  fun fetchPluginDescriptorDataForHeader(plugin: PluginBuildDescriptor, additionalFrontendOnlyPlugin: Boolean): PluginDescriptorDataForHeader {
    val descriptorContainer = platformLayout.descriptorCacheContainer.forPlugin(plugin.dir)
    val fileContent = descriptorContainer.getCachedFileData(PLUGIN_XML_RELATIVE_PATH) ?: error("Cannot find plugin.xml for ${plugin.dir} in the cache")
    return fetchPluginDescriptorDataForHeader(
      fileContent,
      pluginDescriptorJpsModuleName = plugin.layout.mainModule,
      descriptorContainer,
      additionalContainersForEmbeddedFrontend,
      additionalFrontendOnlyPlugin,
    )
  }

  val bundledPluginDescriptorsData = bundledPlugins.map { plugin -> fetchPluginDescriptorDataForHeader(plugin, additionalFrontendOnlyPlugin = false) }
  val additionalFrontendPluginDescriptorsData = additionalFrontendOnlyPlugins.map { plugin -> fetchPluginDescriptorDataForHeader(plugin, additionalFrontendOnlyPlugin = true) }
  return listOf(corePluginDescriptorData) + bundledPluginDescriptorsData + additionalFrontendPluginDescriptorsData
}

private fun fetchPluginDescriptorDataForHeader(
  pluginDescriptorContent: ByteArray,
  pluginDescriptorJpsModuleName: String,
  descriptorContainer: ScopedCachedDescriptorContainer,
  additionalContainersForEmbeddedFrontend: List<ScopedCachedDescriptorContainer>,
  additionalFrontendOnlyPlugin: Boolean,
): PluginDescriptorDataForHeader {
  val parsedContent = parseContentAndXIncludes(input = pluginDescriptorContent, locationSource = pluginDescriptorJpsModuleName)
  val pluginId = parsedContent.pluginId ?: error("<id> tag is not set in plugin.xml in $pluginDescriptorJpsModuleName")
  val contentModules = parsedContent.contentModules.mapNotNull { contentModuleElement ->
    val namespace = contentModuleElement.namespace ?: $$"$${pluginId}_$implicit"
    if (contentModuleElement.name.contains("/")) return@mapNotNull null //todo remove this check after all content modules are extracted to separate JPS modules (IJPL-165543)

    val descriptorName = "${contentModuleElement.name}.xml"
    var moduleXmlData = descriptorContainer.getCachedFileData(descriptorName)
    if (moduleXmlData == null && pluginId == "com.intellij") {
      moduleXmlData = additionalContainersForEmbeddedFrontend.firstNotNullOfOrNull { it.getCachedFileData(descriptorName) }
    }
    require(moduleXmlData != null) { "Cannot find $descriptorName descriptor for plugin.xml in $pluginDescriptorJpsModuleName" }
    val moduleXmlRoot = JDOMUtil.load(moduleXmlData)
    val visibility = parseVisibility(moduleXmlRoot)
    val loadingRule = contentModuleElement.loadingRule.toRuntimeModuleLoadingRule()
    val requiredIfAvailable = contentModuleElement.requiredIfAvailable?.let { RuntimeModuleId.contentModule(it, RuntimeModuleId.DEFAULT_NAMESPACE) }
    ContentModuleRegistrationDataForHeader(contentModuleElement.name, namespace, loadingRule, requiredIfAvailable, visibility)
  }
  return PluginDescriptorDataForHeader(pluginId, pluginDescriptorJpsModuleName, additionalFrontendOnlyPlugin, contentModules.associateBy { it.name })
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

private fun ModuleLoadingRuleValue.toRuntimeModuleLoadingRule(): RuntimeModuleLoadingRule {
  return when (this) {
    ModuleLoadingRuleValue.REQUIRED -> RuntimeModuleLoadingRule.REQUIRED
    ModuleLoadingRuleValue.OPTIONAL -> RuntimeModuleLoadingRule.OPTIONAL
    ModuleLoadingRuleValue.EMBEDDED -> RuntimeModuleLoadingRule.EMBEDDED
    ModuleLoadingRuleValue.ON_DEMAND -> RuntimeModuleLoadingRule.ON_DEMAND
  }
}