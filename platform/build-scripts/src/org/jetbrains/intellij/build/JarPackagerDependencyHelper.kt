// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.*
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.*
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap

internal val useTestSourceEnabled: Boolean = System.getProperty("idea.build.pack.test.source.enabled", "true").toBoolean()

// production-only - JpsJavaClasspathKind.PRODUCTION_RUNTIME
internal class JarPackagerDependencyHelper(private val context: CompilationContext) {
  private val javaExtensionService = JpsJavaExtensionService.getInstance()

  private val libraryCache = ConcurrentHashMap<JpsModule, List<JpsLibraryDependency>>()

  fun getModuleDependencies(moduleName: String): Sequence<String> {
    return getModuleDependencies(context.findRequiredModule(moduleName)).map { it.moduleReference.moduleName }
  }

  fun isPluginModulePackedIntoSeparateJar(module: JpsModule, layout: PluginLayout?, frontendModuleFilter: FrontendModuleFilter): Boolean {
    if (layout != null && !frontendModuleFilter.isModuleCompatibleWithFrontend(layout.mainModule) && frontendModuleFilter.isModuleCompatibleWithFrontend(module.name)) { 
      return true
    }
    val modulesWithExcludedModuleLibraries = layout?.modulesWithExcludedModuleLibraries ?: emptySet()
    return module.name !in modulesWithExcludedModuleLibraries &&
           getLibraryDependencies(module = module, withTests = false).any { it.libraryReference.parentReference is JpsModuleReference }
  }

  fun isTestPluginModule(moduleName: String, module: JpsModule?): Boolean {
    if (!useTestSourceEnabled) {
      return false
    }

    // todo use some marker
    if (moduleName == "intellij.rdct.testFramework" ||
        moduleName == "intellij.platform.split.testFramework" ||
        moduleName == "intellij.python.junit5Tests" ||
        moduleName == "intellij.rdct.tests.distributed") {
      return true
    }

    if (moduleName.contains(".test.")) {
      if (module?.sourceRoots?.none { it.rootType.isForTests } == true) {
        return false
      }

      return moduleName != "intellij.rider.test.framework" &&
             moduleName != "intellij.rider.test.framework.core" &&
             moduleName != "intellij.rider.test.framework.testng" &&
             moduleName != "intellij.rider.test.framework.junit"
    }
    return moduleName.endsWith("._test")
  }

  suspend fun getPluginXmlContent(pluginModule: JpsModule): String {
    val path = "META-INF/plugin.xml"
    var pluginXmlContent = context.readFileContentFromModuleOutput(pluginModule, path, forTests = false)
    if (useTestSourceEnabled && pluginXmlContent == null) {
      pluginXmlContent = context.readFileContentFromModuleOutput(pluginModule, path, forTests = true)
    }
    return pluginXmlContent?.let { String(it, Charsets.UTF_8) }
           ?: throw IllegalStateException("$path not found in ${pluginModule.name} module output")
  }

  suspend fun getPluginIdByModule(pluginModule: JpsModule): String {
    // it is ok to read the plugin descriptor with unresolved x-include as the ID should be specified at the root
    val root = readXmlAsModel(StringReader(getPluginXmlContent(pluginModule)))
    val element = root.getChild("id") ?: root.getChild("name") ?: throw IllegalStateException("Cannot find attribute id or name (module=$pluginModule)")
    return element.content!!
  }

  /**
   * Returns pairs of the module names and the corresponding [com.intellij.ide.plugins.ModuleLoadingRule].
   */
  suspend fun readPluginContentFromDescriptor(pluginModule: JpsModule, moduleOutputPatcher: ModuleOutputPatcher): Sequence<Pair<String, String?>> {
    return readPluginContentFromDescriptor(getResolvedPluginDescriptor(pluginModule, moduleOutputPatcher))
  }

  // plugin patcher should be executed before
  private suspend fun getResolvedPluginDescriptor(pluginModule: JpsModule, moduleOutputPatcher: ModuleOutputPatcher): XmlElement {
    return moduleOutputPatcher.getPatchedPluginXmlIfExists(pluginModule.name)?.let { readXmlAsModel(it) } ?: readXmlAsModel(StringReader(getPluginXmlContent(pluginModule)))
  }

  // The x-include is not resolved. If the plugin.xml includes any files, the content from these included files will not be considered.
  fun readPluginIncompleteContentFromDescriptor(pluginModule: JpsModule, contentModuleFilter: ContentModuleFilter): Sequence<String> {
    val pluginXml = findFileInModuleSources(pluginModule, "META-INF/plugin.xml") ?: return emptySequence()
    return readPluginContentFromDescriptor(readXmlAsModel(pluginXml)).mapNotNull { (moduleName, loadingRule) ->
      if (isOptionalLoadingRule(loadingRule) && !contentModuleFilter.isOptionalModuleIncluded(moduleName, pluginModule.name)) {
        return@mapNotNull null
      }
      moduleName
    }
  }

  fun isOptionalLoadingRule(loadingRule: String?): Boolean = loadingRule != "required" && loadingRule != "embedded"

  private fun readPluginContentFromDescriptor(pluginDescriptor: XmlElement): Sequence<Pair<String, String?>> {
    return sequence {
      for (content in pluginDescriptor.children("content")) {
        for (module in content.children("module")) {
          val moduleName = module.attributes.get("name")?.takeIf { !it.contains('/') } ?: continue
          val loadingRuleString = module.attributes.get("loading")
          yield(moduleName to loadingRuleString)
        }
      }
    }
  }

  private fun getModuleDependencies(module: JpsModule): Sequence<JpsModuleDependency> {
    return sequence {
      for (element in module.dependenciesList.dependencies) {
        if (element is JpsModuleDependency && isProductionRuntime(element, withTests = false)) {
          yield(element)
        }
      }
    }
  }

  fun getLibraryDependencies(module: JpsModule, withTests: Boolean): List<JpsLibraryDependency> {
    return libraryCache.computeIfAbsent(module) {
      val result = mutableListOf<JpsLibraryDependency>()
      for (element in module.dependenciesList.dependencies) {
        if (isProductionRuntime(element = element, withTests = withTests) && element is JpsLibraryDependency) {
          result.add(element)
        }
      }
      if (result.isEmpty()) java.util.List.of() else result
    }
  }

  // cool.module.core has dependency on a library cool-library.
  // And it is a plugin.
  //
  // cool.module.part1 has dependency on cool.module.core AND on library cool-library.
  // And it is a plugin that depends on cool.module.core.
  //
  // We should include cool-library only to cool.module.core (same group).
  fun hasLibraryInDependencyChainOfModuleDependencies(dependentModule: JpsModule, libraryName: String, siblings: Collection<ModuleItem>, withTests: Boolean): Boolean {
    val parentGroup = dependentModule.name.let { it.substring(0, it.lastIndexOf('.')) }
    val prefix = "$parentGroup."
    for (dependency in getModuleDependencies(dependentModule)) {
      val moduleName = dependency.moduleReference.moduleName
      if (moduleName == parentGroup) {
        if (getLibraryDependencies(dependency.module ?: continue, withTests).any { it.libraryReference.libraryName == libraryName }) {
          return true
        }
      }
      else if (moduleName.startsWith(prefix) &&
          siblings.none { it.moduleName == moduleName } &&
          getLibraryDependencies(dependency.module ?: continue, withTests).any { it.libraryReference.libraryName == libraryName }) {
        return true
      }
    }
    return false
  }

  private fun isProductionRuntime(element: JpsDependencyElement, withTests: Boolean): Boolean {
    val scope = javaExtensionService.getDependencyExtension(element)?.scope ?: return false
    if (withTests && scope.isIncludedIn(JpsJavaClasspathKind.TEST_RUNTIME)) {
      return true
    }
    return scope.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)
  }
}
