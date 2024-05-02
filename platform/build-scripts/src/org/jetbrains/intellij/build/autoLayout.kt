// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.*

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
  // for now, check only direct dependencies of the main plugin module
  val childPrefix = "${layout.mainModule}."
  for (name in helper.getModuleDependencies(layout.mainModule)) {
    if ((!name.startsWith(childPrefix) && name != "intellij.platform.commercial.verifier") || addedModules.contains(name)) {
      continue
    }

    val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = layout.getMainJarName(), reason = "<- ${layout.mainModule}")
    addedModules.add(name)
    if (platformLayout.includedModules.contains(moduleItem)) {
      continue
    }

    jarPackager.computeSourcesForModule(item = moduleItem, moduleOutputPatcher = moduleOutputPatcher, layout = layout, searchableOptionSetDescriptor = jarsWithSearchableOptions)
  }

  if (layout.mainModule == "intellij.pycharm.ds.remoteInterpreter") {
    // todo PyCharm team why this module is being incorrectly published
    return
  }

  // check content
  helper.readPluginContentFromDescriptor(context.findRequiredModule(layout.mainModule))
    .filterNot { !addedModules.add(it) }
    .forEach { moduleName ->
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

  // check verifier in all included modules
  val effectiveIncludedNonMainModules = LinkedHashSet<String>(layout.includedModules.size + addedModules.size)
  layout.includedModules.mapTo(effectiveIncludedNonMainModules) { it.moduleName }
  effectiveIncludedNonMainModules.remove(layout.mainModule)
  effectiveIncludedNonMainModules.addAll(addedModules)
  for (moduleName in effectiveIncludedNonMainModules) {
    for (name in helper.getModuleDependencies(moduleName)) {
      if (name != "intellij.platform.commercial.verifier" || addedModules.contains(name)) {
        continue
      }

      val moduleItem = ModuleItem(moduleName = name, relativeOutputFile = layout.getMainJarName(), reason = "<- ${layout.mainModule}")
      addedModules.add(name)
      jarPackager.computeSourcesForModule(
        item = moduleItem,
        moduleOutputPatcher = moduleOutputPatcher,
        layout = layout,
        searchableOptionSetDescriptor = jarsWithSearchableOptions,
      )
    }
  }
}