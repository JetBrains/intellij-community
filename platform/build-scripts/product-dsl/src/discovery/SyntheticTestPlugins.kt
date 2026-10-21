// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.TargetName
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Files
import java.nio.file.Path

const val TEST_MODULE_NAME_PLACEHOLDER: String = "\$TEST_MODULE_NAME\$"

/** The fields of `DevBuildServerSettings` in the build scripts that the generator reads. The build scripts depend on this module, so the class cannot come from there. */
data class DevBuildServerSettings(
  val name: String,
  val enabledForModules: List<String>,
  val disabledForModules: List<String>,
  val jvmArgs: List<String>,
) {
  fun systemProperty(name: String): String? = jvmArgs.firstNotNullOfOrNull { arg -> arg.removePrefix("-D$name=").takeIf { it != arg } }
}

class SyntheticTestPluginProblem(@JvmField val runner: String, @JvmField val message: String)

class SyntheticTestPlugins(
  @JvmField val specsByProduct: Map<String, List<TestPluginSpec>>,
  @JvmField val problems: List<SyntheticTestPluginProblem>,
) {
  companion object {
    @JvmField val EMPTY: SyntheticTestPlugins = SyntheticTestPlugins(specsByProduct = emptyMap(), problems = emptyList())
  }
}

/**
 * The validation counts as bundled the plugins of the product in [bundledPluginsByProduct] and the other entries of
 * `additional.modules`, because the dev build loads both and the plugin graph links a product to the plugins of its DSL
 * spec only. A module without a descriptor gets an empty one when [createMissingDescriptors] allows, because the
 * generator fills its `<dependencies>` in the same run.
 */
fun computeSyntheticTestPlugins(
  runners: List<DevBuildServerSettings>,
  bundledPluginsByProduct: Map<String, Collection<String>>,
  outputProvider: ModuleOutputProvider,
  createMissingDescriptors: Boolean,
): SyntheticTestPlugins {
  val specsByProduct = LinkedHashMap<String, MutableList<TestPluginSpec>>()
  val problems = ArrayList<SyntheticTestPluginProblem>()
  val moduleNames by lazy { outputProvider.getAllModules().map { it.name } }
  for (runner in runners) {
    // the dev build makes one test plugin per run, the one of the entry point module, and only when `additional.modules` names it
    val entryPoint = runner.systemProperty("idea.dev.build.test.entry.point.module") ?: continue
    val additionalModules = runner.systemProperty("additional.modules")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
    val syntheticPlugin = (entryPoint + "._test_plugin").takeIf { it in additionalModules } ?: continue
    val bundledPlugins = additionalModules - syntheticPlugin

    val product = runner.systemProperty("idea.platform.prefix")
    if (product == null) {
      problems.add(SyntheticTestPluginProblem(runner.name, "the runner has no -Didea.platform.prefix=<product> JVM argument"))
      continue
    }
    val productBundledPlugins = bundledPluginsByProduct.get(product)
    if (productBundledPlugins == null) {
      // a runner may name a product the generator derives from no DSL spec: a note, not an error
      println("dev-mode runner '${runner.name}': the product '$product' of -Didea.platform.prefix is not known to the generator, its test plugins are not validated")
      continue
    }

    val targetModules = if (entryPoint == TEST_MODULE_NAME_PLACEHOLDER) enabledModules(runner, moduleNames) else listOf(entryPoint)

    for (targetModule in targetModules) {
      val module = outputProvider.findModule(targetModule)
      if (module == null) {
        problems.add(SyntheticTestPluginProblem(runner.name, "module '$targetModule' is not in the project"))
        continue
      }
      if (!ensureDescriptor(module = module, createMissingDescriptors = createMissingDescriptors, runner = runner.name, problems = problems)) {
        continue
      }
      specsByProduct.getOrPut(product) { ArrayList() }.add(
        TestPluginSpec(
          pluginId = PluginId(targetModule + "._test_plugin"),
          name = "Test plugin for $targetModule (made by the dev build)",
          pluginXmlPath = "",
          spec = productModules { module(targetModule) },
          additionalBundledPluginTargetNames = (productBundledPlugins + bundledPlugins).distinct().map(::TargetName),
          devBuildSynthetic = true,
        )
      )
    }
  }
  return SyntheticTestPlugins(specsByProduct = specsByProduct, problems = problems)
}

private fun enabledModules(runner: DevBuildServerSettings, moduleNames: List<String>): List<String> {
  val result = LinkedHashSet<String>()
  for (pattern in runner.enabledForModules) {
    if (pattern.endsWith("*")) {
      moduleNames.filterTo(result) { it.startsWith(pattern.dropLast(1)) }
    }
    else {
      result.add(pattern)
    }
  }
  result.removeIf { name -> runner.disabledForModules.any { pattern -> matchesName(name, pattern) } }
  return result.toList()
}

/** The rule of `readDevBuildServerSettingsFromIntellijYaml` in the build scripts. */
private fun matchesName(moduleName: String, pattern: String): Boolean {
  return if (pattern.endsWith("*")) moduleName.startsWith(pattern.dropLast(1)) else moduleName == pattern
}

/**
 * The roots are checked on disk and not through the source file index, because the index caches a directory listing:
 * a listing taken before the write would hide the new file from the descriptor cache in the same run.
 */
private fun ensureDescriptor(module: JpsModule, createMissingDescriptors: Boolean, runner: String, problems: MutableList<SyntheticTestPluginProblem>): Boolean {
  val fileName = "${module.name}.xml"
  if (module.sourceRoots.any { Files.isRegularFile(it.path.resolve(fileName)) }) {
    return true
  }
  val testResourceRoot: Path? = module.sourceRoots.filter { it.rootType == JavaResourceRootType.TEST_RESOURCE }.map { it.path }.minOrNull()
  if (testResourceRoot == null) {
    problems.add(SyntheticTestPluginProblem(runner, "module '${module.name}' has no descriptor '$fileName' and no test resource root to create it in;" +
      " add a test resource root with the file (`<idea-plugin></idea-plugin>`) and run Generate Product Layouts"))
    return false
  }
  if (!createMissingDescriptors) {
    problems.add(SyntheticTestPluginProblem(runner, "module '${module.name}' has no descriptor '$fileName'; run Generate Product Layouts to create it"))
    return false
  }
  Files.createDirectories(testResourceRoot)
  Files.writeString(testResourceRoot.resolve(fileName), "<idea-plugin>\n</idea-plugin>\n")
  return true
}
