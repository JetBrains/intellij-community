// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsDependencyElement
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import org.jetbrains.jps.model.module.JpsModuleReference
import java.util.concurrent.ConcurrentHashMap

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
    return !modulesWithExcludedModuleLibraries.contains(module.name) &&
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

    // modules containing tests only as per https://youtrack.jetbrains.com/articles/IJPL-A-62
    if (moduleName.endsWith(".tests")) {
      return true
    }

    if (moduleName.contains(".test.")) {
      if (module?.sourceRoots?.none { it.rootType.isForTests } == true) {
        return false
      }

      return moduleName != "intellij.rider.test.framework" &&
             moduleName != "intellij.rider.test.framework.core" &&
             moduleName != "intellij.rider.test.framework.testng" &&
             moduleName != "intellij.rider.test.framework.junit" &&
             moduleName != "intellij.rider.test.framework.junit5"
    }
    return moduleName.endsWith("._test")
  }

  fun getPluginIdByModule(pluginModule: JpsModule): String {
    // it is ok to read the plugin descriptor with unresolved x-include as the ID should be specified at the root
    val root = readXmlAsModel(getUnprocessedPluginXmlContent(module = pluginModule, context = context))
    val element = root.getChild("id") ?: root.getChild("name") ?: throw IllegalStateException("Cannot find attribute id or name (module=$pluginModule)")
    return element.content!!
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

  private fun getModuleDependencies(module: JpsModule): Sequence<JpsModuleDependency> {
    return sequence {
      for (element in module.dependenciesList.dependencies) {
        if (element is JpsModuleDependency && isProductionRuntime(element = element, withTests = false)) {
          yield(element)
        }
      }
    }
  }

  fun getLibraryDependencies(module: JpsModule, withTests: Boolean): List<JpsLibraryDependency> {
    //TODO Please write some sane code here, caching is broken, a proper caching crashes dev build
    if (module.name == "intellij.python.pyproject" && withTests) {
      return java.util.List.of()
    }
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

internal fun readPluginContentFromDescriptor(pluginDescriptor: XmlElement): Sequence<Pair<String, String?>> {
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

internal fun isOptionalLoadingRule(loadingRule: String?): Boolean = loadingRule != "required" && loadingRule != "embedded"
