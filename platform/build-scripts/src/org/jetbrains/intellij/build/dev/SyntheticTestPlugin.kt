// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.build.dev

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.findUnprocessedDescriptorContent
import org.jetbrains.intellij.build.impl.PRODUCT_DESCRIPTOR_META_PATH
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.isTestModule
import org.jetbrains.intellij.build.productLayout.generator.isTestSupportContentModule
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files

/*
 * The plugin has no module in the project, so the build knows it by its name. The content is computed when the plugin is
 * built, because it depends on what the product registers.
 *
 * The content follows the `<dependencies>` of the module descriptors and not the JPS graph: the loader resolves a class
 * of a content module only through the modules its descriptor declares, then the core loader, so a module no descriptor
 * names is unreachable whatever the `.iml` says.
 */

private const val JETBRAINS_NAMESPACE = "jetbrains"

private const val LIBRARY_MODULE_PREFIX = "intellij.libraries."

/**
 * Test support modules with production roots that the rule of the generator does not name and no product registers: the
 * platform packs `intellij.java.rt` as a plain jar, and the rest exist for test runs only.
 */
private val TEST_SUPPORT_MODULES = setOf(
  "intellij.java.rt",
  "intellij.codeowners.runtime.resolver",
  "intellij.codeowners",
  "intellij.codeowners.monorepo.resolver",
  "intellij.tools.ide.starter",
  "intellij.tools.ide.util.common",
  "intellij.driver.sdk",
  "intellij.rider.test.build.shared",
)

/**
 * The content every synthetic plugin starts from.
 *
 * `intellij.platform.testFramework`: the `ProjectManager` registration of the core, in `intellij.platform.ide.impl.xml`,
 * names `com.intellij.project.TestProjectManager` as the unit-test implementation. That class is in the test framework,
 * and the core cannot depend on the framework, because the framework depends on the core. The core loader of a dev build
 * cannot load the class, so `ProjectManager` works only through the override in `intellij.platform.testFramework.xml`.
 * That registration exists only when the module is loaded as plugin content. An `.iml` rarely declares the module, because
 * a flat test classpath gets it for free from `TEST_FRAMEWORK_DEPS` of `tests-options.bzl`. The walk of the descriptors
 * therefore does not reliably reach it.
 *
 * `intellij.tools.testsBootstrap`: the runner needs its JUnit engines, which a flat test classpath has from the same list.
 */
@VisibleForTesting
internal val BASE_MODULES: List<String> = listOf("intellij.platform.testFramework", "intellij.tools.testsBootstrap")

/** The build and the test run share the JVM, so the property `TestMain` reads names the entry point module. */
internal fun syntheticTestPluginModuleName(): String? = System.getProperty("idea.dev.build.test.entry.point.module")?.plus("._test_plugin")

internal fun syntheticTestPluginXml(pluginModuleName: String, platformLayout: PlatformLayout, context: BuildContext): String {
  val targetModuleName = pluginModuleName.removeSuffix("._test_plugin")
  return spanBuilder("make test plugin").setAttribute("plugin", pluginModuleName).use {
    val provided = providedModules(platformLayout = platformLayout, bundledPlugins = otherPluginLayouts(pluginModuleName, context), outputProvider = context.outputProvider)
    val content = computeSyntheticTestPluginContent(targetModuleName = targetModuleName, outputProvider = context.outputProvider, provided = provided)
    context.messages.info("Made the test plugin '$pluginModuleName' for module '$targetModuleName' with ${content.size} content modules: ${content.joinToString(" ")}")
    renderSyntheticTestPluginXml(pluginModuleName = pluginModuleName, targetModuleName = targetModuleName, content = content)
  }
}

/** The set `devModePluginCandidates` takes before the variant selection: a plugin.xml is the same in every variant of a plugin. */
private fun otherPluginLayouts(pluginModuleName: String, context: BuildContext): List<PluginLayout> {
  val names = LinkedHashSet(context.getBundledPluginModules())
  System.getProperty("additional.modules")?.splitToSequence(',')?.map { it.trim() }?.filterTo(names) { it.isNotEmpty() }
  names.remove(pluginModuleName)
  return getPluginLayoutsByJpsModuleNames(names, context.productProperties.productLayout)
    .filter { isPluginApplicable(bundledMainModuleNames = names, plugin = it, os = OsFamily.currentOs, arch = JvmArchitecture.currentJvmArch, context = context) }
}

/**
 * A module resolves a `<dependencies><module name="..."/>` only through a registration in the `jetbrains` namespace; a
 * module a jar merely packs, like `idea_rt.jar` of `intellij.java.rt`, resolves nothing. A module registered in a
 * `<content>` block without a namespace is its plugin's own copy, which no other plugin resolves, so the synthetic plugin
 * copies its own.
 */
@VisibleForTesting
internal fun providedModules(platformLayout: PlatformLayout, bundledPlugins: List<PluginLayout>, outputProvider: ModuleOutputProvider): Set<String> {
  val provided = HashSet<String>()
  fun collect(pluginXml: XmlElement) {
    for (content in pluginXml.children) {
      if (content.name == "content" && content.getAttributeValue("namespace") == JETBRAINS_NAMESPACE) {
        content.children.filter { it.name == "module" }.mapNotNullTo(provided) { it.getAttributeValue("name") }
      }
    }
  }

  // the product descriptor is cached while the platform layout is computed; a layout built without it (a test) falls back to its product modules
  val productDescriptor = platformLayout.descriptorCacheContainer.forPlatform(platformLayout).getCachedFileData(PRODUCT_DESCRIPTOR_META_PATH)
  if (productDescriptor == null) {
    platformLayout.includedModules.filter { it.isProductModule() }.mapTo(provided) { it.moduleName }
  }
  else {
    collect(readXmlAsModel(productDescriptor))
  }

  for (layout in bundledPlugins) {
    provided.add(layout.mainModule)
    val pluginXml = readPluginXml(layout.mainModule, outputProvider) ?: continue
    collect(pluginXml)
    // a `<content>` block may sit in an included file, which is in the plugin's own modules
    for (include in pluginXml.children) {
      if (include.name != "include") {
        continue
      }
      val href = include.getAttributeValue("href")?.trimStart('/') ?: continue
      val includedFile = layout.includedModules.asSequence()
        .mapNotNull { outputProvider.findModule(it.moduleName) }
        .firstNotNullOfOrNull { outputProvider.findFileInModuleSources(it, href, onlyProductionSources = true) }
      if (includedFile != null) {
        collect(readXmlAsModel(Files.readAllBytes(includedFile)))
      }
    }
  }
  return provided
}

private fun readPluginXml(mainModule: String, outputProvider: ModuleOutputProvider): XmlElement? {
  val module = outputProvider.findModule(mainModule) ?: return null
  val content = findUnprocessedDescriptorContent(module = module, path = PLUGIN_XML_RELATIVE_PATH, outputProvider = outputProvider) ?: return null
  return readXmlAsModel(content)
}

@VisibleForTesting
internal fun computeSyntheticTestPluginContent(targetModuleName: String, outputProvider: ModuleOutputProvider, provided: Set<String>): List<String> {
  val content = LinkedHashSet<String>()
  val rejected = ArrayList<String>()

  fun collect(moduleName: String, reachedFrom: String?) {
    if (moduleName in provided || moduleName in content) {
      return
    }
    val module = outputProvider.findRequiredModule(moduleName)
    val reached = if (reachedFrom == null) "'$moduleName'" else "'$moduleName' (reached from $reachedFrom)"
    // the descriptor of a test module is under a test root
    val descriptorFile = outputProvider.findFileInModuleSources(module, "$moduleName.xml", onlyProductionSources = false)
                         ?: error(
                           "Cannot make a test plugin for '$targetModuleName': $reached has no descriptor." +
                           " Create '$moduleName.xml' with '<idea-plugin></idea-plugin>' in a resource root of the module and run 'Generate Product Layouts'" +
                           " to fill its <dependencies>."
                         )
    // a test runs against the product's copy of production code, so the plugin copies no other production module
    if (!(module.isTestModule() || moduleName in TEST_SUPPORT_MODULES || moduleName.startsWith(LIBRARY_MODULE_PREFIX) || isTestSupportContentModule(ContentModuleName(moduleName), descriptorFile))) {
      rejected.add(reached)
      return
    }
    content.add(moduleName)
    for (dependency in readXmlAsModel(Files.readAllBytes(descriptorFile)).getChild("dependencies")?.children.orEmpty()) {
      if (dependency.name == "module") {
        collect(dependency.getAttributeValue("name") ?: continue, reachedFrom = moduleName)
      }
    }
  }

  collect(targetModuleName, reachedFrom = null)
  for (baseModuleName in BASE_MODULES) {
    collect(baseModuleName, reachedFrom = null)
  }

  if (rejected.isNotEmpty()) {
    error(
      "Cannot make a test plugin for '$targetModuleName': these modules have production roots, are not test support, and the product does not provide them:\n" +
      rejected.joinToString("\n") { "  $it" } +
      "\nA test runs against the product's copy of production code. Run the test in the product that has the module, make it test support, or drop the dependency."
    )
  }
  return content.toList()
}

@VisibleForTesting
internal fun renderSyntheticTestPluginXml(pluginModuleName: String, targetModuleName: String, content: List<String>): String {
  return buildString {
    append("<idea-plugin>\n")
    append("  <id>").append(pluginModuleName).append("</id>\n")
    append("  <name>Test plugin for ").append(targetModuleName).append(" (made by the dev build)</name>\n")
    append("  <vendor>JetBrains</vendor>\n")
    append("\n")
    append("  <content namespace=\"").append(JETBRAINS_NAMESPACE).append("\">\n")
    for (module in content) {
      append("    <module name=\"").append(module).append("\"/>\n")
    }
    append("  </content>\n")
    append("</idea-plugin>\n")
  }
}
