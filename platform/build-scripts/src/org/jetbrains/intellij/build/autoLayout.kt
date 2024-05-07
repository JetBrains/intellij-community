// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.*

private const val VERIFIER_MODULE = "intellij.platform.commercial.verifier"

internal suspend fun inferModuleSources(
  layout: PluginLayout,
  addedModules: MutableSet<String>,
  platformLayout: PlatformLayout,
  helper: JarPackagerDependencyHelper,
  jarPackager: JarPackager,
  moduleOutputPatcher: ModuleOutputPatcher,
  jarsWithSearchableOptions: SearchableOptionSetDescriptor?,
  context: BuildContext,
) {
  // First, check the content. This is done prior to everything else since we might configure a custom relativeOutputFile.
  for (moduleName in helper.readPluginContentFromDescriptor(context.findRequiredModule(layout.mainModule))) {
    // todo PyCharm team why this module is being incorrectly published
    if (layout.mainModule == "intellij.pycharm.ds.remoteInterpreter" || !addedModules.add(moduleName)) {
      continue
    }

    val descriptor = readXmlAsModel(context.findFileInModuleSources(moduleName, "$moduleName.xml")!!)
    jarPackager.computeSourcesForModule(
      item = ModuleItem(
        moduleName = moduleName,
        // relative path with `/` is always packed by dev-mode, so, we don't need to fix resolving for now and can improve it later
        relativeOutputFile = if (descriptor.getAttributeValue("package") == null) "modules/$moduleName.jar" else layout.getMainJarName(),
        reason = "<- ${layout.mainModule} (plugin content)",
      ),
      moduleOutputPatcher = moduleOutputPatcher,
      layout = layout,
      searchableOptionSetDescriptor = jarsWithSearchableOptions,
    )
  }

  // for now, check only direct dependencies of the main plugin module
  val childPrefix = "${layout.mainModule.removeSuffix(".plugin")}."
  for (name in helper.getModuleDependencies(layout.mainModule)) {
    if ((!name.startsWith(childPrefix) && name != VERIFIER_MODULE)) {
      continue
    }

    if (!addedModules.add(name)) {
      continue
    }

    val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = layout.getMainJarName(), reason = "<- ${layout.mainModule}")
    if (isIncludedIntoAnotherPlugin(platformLayout = platformLayout, moduleItem = moduleItem, context = context, layout = layout, moduleName = name)) {
      continue
    }

    jarPackager.computeSourcesForModule(item = moduleItem, moduleOutputPatcher = moduleOutputPatcher, layout = layout, searchableOptionSetDescriptor = jarsWithSearchableOptions)
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

      val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = layout.getMainJarName(), reason = "<- ${layout.mainModule}")
      addedModules.add(name)
      jarPackager.computeSourcesForModule(item = moduleItem, moduleOutputPatcher = moduleOutputPatcher, layout = layout, searchableOptionSetDescriptor = jarsWithSearchableOptions)
    }
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