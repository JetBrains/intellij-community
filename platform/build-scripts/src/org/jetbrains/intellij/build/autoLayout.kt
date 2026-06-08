// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.BUILT_IN_HELP_MODULE_NAME
import org.jetbrains.intellij.build.impl.DescriptorCacheWriter
import org.jetbrains.intellij.build.impl.JarPackager
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.contentModuleNameToDescriptorFileName
import org.jetbrains.jps.model.module.JpsModule

internal suspend fun inferModuleSources(
  layout: PluginLayout,
  addedModules: MutableSet<String>,
  platformLayout: PlatformLayout,
  helper: JarPackagerDependencyHelper,
  jarPackager: JarPackager,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  context: BuildContext,
) {
  val frontendModuleFilter = context.getFrontendModuleFilter()
  // for now, check only direct dependencies of the main plugin module
  val childPrefix = "${layout.mainModule.removeSuffix(".plugin")}."
  for (name in helper.getModuleDependencies(layout.mainModule)) {
    if (!name.startsWith(childPrefix)) {
      continue
    }

    if (!addedModules.add(name)) {
      continue
    }

    val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = getDefaultJarName(layout, name, frontendModuleFilter), reason = "<- ${layout.mainModule}")
    if (isIncludedIntoAnotherPlugin(platformLayout = platformLayout, moduleItem = moduleItem, layout = layout, moduleName = name, context = context)) {
      continue
    }

    jarPackager.computeSourcesForModule(item = moduleItem, layout = layout, searchableOptionSet = searchableOptionSet)
  }
}

internal suspend fun computeModuleSourcesByContent(
  helper: JarPackagerDependencyHelper,
  context: BuildContext,
  pluginLayout: PluginLayout,
  addedModules: MutableSet<String>,
  jarPackager: JarPackager,
  searchableOptionSet: SearchableOptionSetDescriptor?,
  modulesWithCustomPath: HashSet<String>,
  pluginCachedDescriptorContainer: ScopedCachedDescriptorContainer,
) {
  // plugin patcher must be executed before
  val cachedFileData = pluginCachedDescriptorContainer.getCachedFileData(PLUGIN_XML_RELATIVE_PATH)
  // quick fix of clion installer - not clear yet why a proper fix didn't help
  if (cachedFileData == null && pluginLayout.mainModule == BUILT_IN_HELP_MODULE_NAME) {
    return
  }

  val element = requireNotNull(cachedFileData) {
    "Plugin descriptor '$PLUGIN_XML_RELATIVE_PATH' is not found in cached descriptor container, " +
    "plugin patcher must be executed before (pluginMainModule=${pluginLayout.mainModule}, pluginCachedDescriptorContainer=$pluginCachedDescriptorContainer)"
  }.let { JDOMUtil.load(it) }

  val pluginContent = sequence {
    for (content in element.getChildren("content")) {
      for (module in content.getChildren("module")) {
        val moduleName = module.getAttributeValue("name")?.takeIf { !it.contains('/') } ?: continue
        val loadingRuleString = module.getAttributeValue("loading")
        yield(moduleName to loadingRuleString)
      }
    }
  }

  val frontendModuleFilter = context.getFrontendModuleFilter()
  val descriptorCacheWriter = pluginCachedDescriptorContainer.write()
  for ((moduleName, loadingRule) in pluginContent) {
    if (!addedModules.add(moduleName)) {
      continue
    }

    val relativeOutputFile = computeOutputJarPath(
      moduleName = moduleName,
      loadingRule = loadingRule,
      modulesWithCustomPath = modulesWithCustomPath,
      pluginLayout = pluginLayout,
      frontendModuleFilter = frontendModuleFilter,
      helper = helper,
      context = context,
      pluginCachedDescriptorContainer = pluginCachedDescriptorContainer,
      descriptorCacheWriter = descriptorCacheWriter,
    )
    if (relativeOutputFile == null) {
      addedModules.remove(moduleName)
      continue
    }

    jarPackager.computeSourcesForModule(
      item = ModuleItem(
        moduleName = moduleName,
        relativeOutputFile = relativeOutputFile,
        reason = "<- ${pluginLayout.mainModule} (plugin content)",
      ),
      layout = pluginLayout,
      searchableOptionSet = searchableOptionSet,
    )
  }
  descriptorCacheWriter.apply()
}

private suspend fun computeOutputJarPath(
  moduleName: String,
  loadingRule: String?,
  modulesWithCustomPath: Set<String>,
  pluginLayout: PluginLayout,
  frontendModuleFilter: FrontendModuleFilter,
  helper: JarPackagerDependencyHelper,
  context: BuildContext,
  pluginCachedDescriptorContainer: ScopedCachedDescriptorContainer,
  descriptorCacheWriter: DescriptorCacheWriter,
): String? {
  if (loadingRule == "embedded") {
    return computeEmbeddedOutputJarPath(
      moduleName = moduleName,
      modulesWithCustomPath = modulesWithCustomPath,
      pluginLayout = pluginLayout,
      frontendModuleFilter = frontendModuleFilter,
      packIntoPluginJar = hasPackContentIntoPluginJarMarker(
        findContentModuleDescriptorData(
          moduleName = moduleName,
          module = null,
          context = context,
          pluginCachedDescriptorContainer = pluginCachedDescriptorContainer,
          descriptorCacheWriter = descriptorCacheWriter,
        )
      ),
    )
  }

  // Case 3: Non-embedded modules → check descriptor for separate jar need
  val needsSeparateJar = checkNeedsSeparateJar(
    moduleName = moduleName,
    pluginLayout = pluginLayout,
    frontendModuleFilter = frontendModuleFilter,
    helper = helper,
    context = context,
    pluginCachedDescriptorContainer = pluginCachedDescriptorContainer,
    descriptorCacheWriter = descriptorCacheWriter,
  )

  return when {
    needsSeparateJar -> "modules/$moduleName.jar"
    modulesWithCustomPath.contains(moduleName) -> null
    else -> getDefaultJarName(pluginLayout, moduleName, frontendModuleFilter)
  }
}

private suspend fun checkNeedsSeparateJar(
  moduleName: String,
  pluginLayout: PluginLayout,
  frontendModuleFilter: FrontendModuleFilter,
  helper: JarPackagerDependencyHelper,
  context: BuildContext,
  pluginCachedDescriptorContainer: ScopedCachedDescriptorContainer,
  descriptorCacheWriter: DescriptorCacheWriter,
): Boolean {
  val module = context.outputProvider.findRequiredModule(moduleName)
  val descriptorData = requireNotNull(
    findContentModuleDescriptorData(
      moduleName = moduleName,
      module = module,
      context = context,
      pluginCachedDescriptorContainer = pluginCachedDescriptorContainer,
      descriptorCacheWriter = descriptorCacheWriter,
    )
  ) {
    "${contentModuleNameToDescriptorFileName(moduleName)} not found in module $moduleName"
  }
  return needsSeparateJar(
    descriptorData = descriptorData,
    module = module,
    pluginLayout = pluginLayout,
    frontendModuleFilter = frontendModuleFilter,
    helper = helper,
  )
}

private fun needsSeparateJar(
  descriptorData: ByteArray,
  module: JpsModule,
  pluginLayout: PluginLayout,
  frontendModuleFilter: FrontendModuleFilter,
  helper: JarPackagerDependencyHelper,
): Boolean {
  if (hasPackContentIntoPluginJarMarker(descriptorData)) {
    return false
  }
  val descriptor = readXmlAsModel(descriptorData)
  return descriptor.getAttributeValue("package") == null ||
         helper.isPluginModulePackedIntoSeparateJar(module, pluginLayout, frontendModuleFilter)
}

private fun computeEmbeddedOutputJarPath(
  moduleName: String,
  modulesWithCustomPath: Set<String>,
  pluginLayout: PluginLayout,
  frontendModuleFilter: FrontendModuleFilter,
  packIntoPluginJar: Boolean,
): String? {
  return when {
    modulesWithCustomPath.contains(moduleName) -> null
    packIntoPluginJar -> getDefaultJarName(pluginLayout, moduleName, frontendModuleFilter)
    else -> "$moduleName.jar"
  }
}

private fun hasPackContentIntoPluginJarMarker(descriptorData: ByteArray?): Boolean {
  return descriptorData != null && PACK_CONTENT_INTO_PLUGIN_JAR_MARKER_REGEX.containsMatchIn(descriptorData.decodeToString())
}

private suspend fun findContentModuleDescriptorData(
  moduleName: String,
  module: JpsModule?,
  context: BuildContext,
  pluginCachedDescriptorContainer: ScopedCachedDescriptorContainer,
  descriptorCacheWriter: DescriptorCacheWriter,
): ByteArray? {
  val descriptorFileName = contentModuleNameToDescriptorFileName(moduleName)
  val cachedDescriptorData = pluginCachedDescriptorContainer.getCachedFileData(descriptorFileName)
  if (cachedDescriptorData != null) {
    return cachedDescriptorData
  }

  val descriptorModule = module ?: context.outputProvider.findRequiredModule(moduleName)
  val descriptorData = findUnprocessedDescriptorContent(
    module = descriptorModule,
    path = descriptorFileName,
    outputProvider = context.outputProvider,
  ) ?: return null
  descriptorCacheWriter.put(descriptorFileName, descriptorData)
  return descriptorData
}

private fun getDefaultJarName(layout: PluginLayout, moduleName: String, frontendModuleFilter: FrontendModuleFilter): String {
  if (frontendModuleFilter.isModuleCompatibleWithFrontend(layout.mainModule) || !frontendModuleFilter.isModuleCompatibleWithFrontend(moduleName)) {
    return layout.getMainJarName()
  }
  else {
    return layout.getMainJarName().removeSuffix(".jar") + "-frontend.jar"
  }
}

private fun isIncludedIntoAnotherPlugin(platformLayout: PlatformLayout, moduleItem: ModuleItem, layout: PluginLayout, moduleName: String, context: BuildContext): Boolean {
  return when {
    platformLayout.includedModules.contains(moduleItem) -> true
    platformLayout.includedModules.any { it.moduleName == moduleName } -> true
    else -> context.productProperties.productLayout.pluginLayouts.any { otherPluginLayout ->
      otherPluginLayout !== layout && otherPluginLayout.includedModules.any { it.moduleName == moduleName }
    }
  }
}

private val PACK_CONTENT_INTO_PLUGIN_JAR_MARKER_REGEX = Regex("""<!--\s+intellij-build:\s+pack-content-into-plugin-jar\s+-->""")
