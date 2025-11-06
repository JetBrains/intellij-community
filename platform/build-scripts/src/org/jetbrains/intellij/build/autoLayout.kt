// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.BUILT_IN_HELP_MODULE_NAME
import org.jetbrains.intellij.build.impl.JarPackager
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.ScopedCachedDescriptorContainer
import org.jetbrains.intellij.build.impl.contentModuleNameToDescriptorFileName

private const val VERIFIER_MODULE = "intellij.platform.commercial.verifier"

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
    if ((!name.startsWith(childPrefix) && name != VERIFIER_MODULE)) {
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

  // check verifier in all included modules
  val effectiveIncludedNonMainModules = LinkedHashSet<String>(layout.includedModules.size + addedModules.size)
  layout.includedModules.mapTo(effectiveIncludedNonMainModules) { it.moduleName }
  effectiveIncludedNonMainModules.remove(layout.mainModule)
  effectiveIncludedNonMainModules.addAll(addedModules)
  for (moduleName in effectiveIncludedNonMainModules) {
    for (name in helper.getModuleDependencies(moduleName)) {
      if (name != VERIFIER_MODULE || addedModules.contains(name)) {
        continue
      }

      val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = getDefaultJarName(layout, name, frontendModuleFilter), reason = "<- ${layout.mainModule}")
      addedModules.add(name)
      jarPackager.computeSourcesForModule(item = moduleItem, layout = layout, searchableOptionSet = searchableOptionSet)
    }
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

    val useSeparateJar: Boolean
    if (loadingRule == "embedded") {
      useSeparateJar = false
    }
    else {
      val module = context.findRequiredModule(moduleName)
      val descriptorFileName = contentModuleNameToDescriptorFileName(moduleName)
      var descriptorData = pluginCachedDescriptorContainer.getCachedFileData(descriptorFileName)
      if (descriptorData == null) {
        descriptorData = requireNotNull(findUnprocessedDescriptorContent(module = module, path = descriptorFileName, context = context)) {
          "$descriptorFileName not found in module $moduleName"
        }
        descriptorCacheWriter.put(descriptorFileName, descriptorData)
      }
      val descriptor = readXmlAsModel(descriptorData)
      useSeparateJar = (descriptor.getAttributeValue("package") == null || helper.isPluginModulePackedIntoSeparateJar(module, pluginLayout, frontendModuleFilter))
    }
    if (!useSeparateJar && modulesWithCustomPath.contains(moduleName)) {
      addedModules.remove(moduleName)
      continue
    }

    jarPackager.computeSourcesForModule(
      item = ModuleItem(
        moduleName = moduleName,
        // relative path with `/` is always packed by dev-mode, so we don't need to fix resolving for now and can improve it later
        relativeOutputFile = if (useSeparateJar) "modules/$moduleName.jar" else getDefaultJarName(pluginLayout, moduleName, frontendModuleFilter),
        reason = "<- ${pluginLayout.mainModule} (plugin content)",
      ),
      layout = pluginLayout,
      searchableOptionSet = searchableOptionSet,
    )
  }
  descriptorCacheWriter.apply()
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
    moduleName == VERIFIER_MODULE -> false
    platformLayout.includedModules.contains(moduleItem) -> true
    platformLayout.includedModules.any { it.moduleName == moduleName } -> true
    else -> context.productProperties.productLayout.pluginLayouts.any { otherPluginLayout ->
      otherPluginLayout !== layout && otherPluginLayout.includedModules.any { it.moduleName == moduleName }
    }
  }
}