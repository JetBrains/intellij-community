// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.readXmlAsModel
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.impl.*

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

    val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = layout.getDefaultJarName(name, frontendModuleFilter), reason = "<- ${layout.mainModule}")
    if (isIncludedIntoAnotherPlugin(platformLayout = platformLayout, moduleItem = moduleItem, context = context, layout = layout, moduleName = name)) {
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

      val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = layout.getDefaultJarName(name, frontendModuleFilter), reason = "<- ${layout.mainModule}")
      addedModules.add(name)
      jarPackager.computeSourcesForModule(item = moduleItem, layout = layout, searchableOptionSet = searchableOptionSet)
    }
  }
}

internal suspend fun computeModuleSourcesByContent(
  helper: JarPackagerDependencyHelper,
  context: BuildContext,
  layout: PluginLayout,
  addedModules: MutableSet<String>,
  jarPackager: JarPackager,
  searchableOptionSet: SearchableOptionSetDescriptor?
) {
  val frontendModuleFilter = context.getFrontendModuleFilter()
  val contentModuleFilter = context.getContentModuleFilter()
  for ((moduleName, loadingRule) in helper.readPluginContentFromDescriptor(context.findRequiredModule(layout.mainModule), jarPackager.moduleOutputPatcher)) {
    if (helper.isOptionalLoadingRule(loadingRule) && !contentModuleFilter.isOptionalModuleIncluded(moduleName, pluginMainModuleName = layout.mainModule)) {
      Span.current().addEvent("Module '$moduleName' is excluded from plugin '${layout.mainModule}' by $contentModuleFilter")
      continue
    }

    // CWM plugin is overcomplicated without any valid reason - it must be refactored
    if (moduleName == "intellij.driver.backend.split" || !addedModules.add(moduleName)) {
      continue
    }

    val module = context.findRequiredModule(moduleName)
    val descriptor = readXmlAsModel(findFileInModuleSources(module, "$moduleName.xml") ?: error("$moduleName.xml not found in module $moduleName sources"))
    val useSeparateJar = (descriptor.getAttributeValue("package") == null || 
                          helper.isPluginModulePackedIntoSeparateJar(module, layout, frontendModuleFilter)) && loadingRule != "embedded"
    jarPackager.computeSourcesForModule(
      item = ModuleItem(
        moduleName = moduleName,
        // relative path with `/` is always packed by dev-mode, so, we don't need to fix resolving for now and can improve it later
        relativeOutputFile = if (useSeparateJar) "modules/$moduleName.jar" else layout.getDefaultJarName(moduleName, frontendModuleFilter),
        reason = "<- ${layout.mainModule} (plugin content)",
      ),
      layout = layout,
      searchableOptionSet = searchableOptionSet,
    )
  }
}

private fun PluginLayout.getDefaultJarName(moduleName: String, frontendModuleFilter: FrontendModuleFilter): String {
  return if (moduleName != VERIFIER_MODULE && !frontendModuleFilter.isModuleCompatibleWithFrontend(mainModule) && frontendModuleFilter.isModuleCompatibleWithFrontend(moduleName)) {
    getMainJarName().removeSuffix(".jar") + "-frontend.jar"
  }
  else {
    getMainJarName()
  }
}

private fun isIncludedIntoAnotherPlugin(platformLayout: PlatformLayout, moduleItem: ModuleItem, context: BuildContext, layout: PluginLayout, moduleName: String): Boolean {
  if (moduleName == VERIFIER_MODULE) {
    return false
  }

  return platformLayout.includedModules.contains(moduleItem) ||
         context.productProperties.productLayout.pluginLayouts.any { otherPluginLayout ->
           otherPluginLayout !== layout && otherPluginLayout.includedModules.any { it.moduleName == moduleName }
         }
}