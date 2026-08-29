// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.findFileInModuleSources
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout

/**
 * The plugin sets that [ProductModulesLayout.pluginExclusionVariants] describes.
 */
@Internal
class PluginVariants private constructor(
  /** The main modules that each variant loads. Holds at least one element. */
  @JvmField val variants: List<List<String>>,
  /** The main module of a plugin that no variant loads, and the id of that plugin. */
  @JvmField val excludedEverywhere: Map<String, String>,
) {
  companion object {
    /**
     * Resolve [ProductModulesLayout.pluginExclusionVariants] against [pluginsToPublish].
     *
     * A variant names a plugin id, and a consumer loads a main module, so this reads the descriptor of each plugin to learn
     * its id. A plugin whose descriptor states no id reaches every variant, because no variant can name it.
     * A plugin that the product bundles is in every variant already, so no variant names it again.
     *
     * A variant must also name each dependent of a plugin that it leaves out. See
     * [ProductModulesLayout.pluginExclusionVariants].
     *
     * @param excludedMainModules the main modules that every variant leaves out. The caller states what its own consumer
     * cannot load, so one variant model serves a consumer with an extra limit.
     */
    internal fun resolvePluginVariants(
      pluginsToPublish: Collection<PluginLayout>,
      context: BuildContext,
      excludedMainModules: Set<String> = emptySet(),
    ): PluginVariants {
      val bundled = context.getBundledPluginModules().toSet()
      val mainModules = pluginsToPublish.asSequence()
        .map { it.mainModule }
        .filter { !bundled.contains(it) }
        .distinct()
        .sorted()
        .toList()

      val declaredVariants = context.productProperties.productLayout.pluginExclusionVariants
      if (mainModules.isEmpty() || (declaredVariants.isEmpty() && excludedMainModules.isEmpty())) {
        return PluginVariants(variants = listOf(mainModules), excludedEverywhere = emptyMap())
      }

      // one variant that excludes nothing, so the excluded main modules still drop out
      val exclusionVariants = declaredVariants.ifEmpty { listOf(emptySet()) }

      val idByMainModule = mainModules.associateWith { mainModule ->
        readPluginId(moduleName = mainModule, outputProvider = context.outputProvider)
      }

      val candidates = mainModules.filter { !excludedMainModules.contains(it) }
      val variants = exclusionVariants.map { excluded ->
        // a plugin whose descriptor states no id stays, because no variant can name it
        candidates.filter { idByMainModule[it]?.let { id -> !excluded.contains(id) } ?: true }
      }
      val loadedSomewhere = variants.flatMapTo(HashSet()) { it }
      val excludedEverywhere = idByMainModule.filter { !loadedSomewhere.contains(it.key) }
      return PluginVariants(variants = variants, excludedEverywhere = excludedEverywhere)
    }
  }
}

/**
 * Read the id of the plugin that [moduleName] is the main module of.
 *
 * Returns `null` when the module holds no plugin descriptor, or when the descriptor states no id.
 * The reader resolves no `xi:include`, so it finds no id that an included file states.
 */
private fun readPluginId(moduleName: String, outputProvider: ModuleOutputProvider): String {
  val module = outputProvider.findModule(moduleName) ?: error("Module $moduleName is missing in output")
  val descriptor = findFileInModuleSources(module = module, relativePath = "META-INF/plugin.xml", onlyProductionSources = true)
                   ?: error("Module $module does not declare plugin.xml")
  return parsePluginId(JDOMUtil.load(descriptor)) ?: error("Module $module does not declare a plugin id")
}

/**
 * Take the id out of one plugin descriptor. The name stands in for a missing id.
 *
 * Returns `null` when the descriptor states no id and no name.
 */
@Internal
fun parsePluginId(xml: Element): String? {
  return xml.getChildTextTrim("id")?.takeIf { it.isNotEmpty() }
         ?: xml.getChildTextTrim("name")?.takeIf { it.isNotEmpty() }
}
