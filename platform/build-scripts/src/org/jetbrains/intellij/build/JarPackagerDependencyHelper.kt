// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.*
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private val useTestSourceEnabled = System.getProperty("idea.build.pack.test.source.enabled", "false").toBoolean()

// production-only - JpsJavaClasspathKind.PRODUCTION_RUNTIME
internal class JarPackagerDependencyHelper(private val context: BuildContext) {
  private val javaExtensionService = JpsJavaExtensionService.getInstance()

  private val libraryCache = ConcurrentHashMap<JpsModule, List<JpsLibraryDependency>>()

  fun getModuleDependencies(moduleName: String): Sequence<String> {
    return getModuleDependencies(context.findRequiredModule(moduleName)).map { it.moduleReference.moduleName }
  }

  fun isPluginModulePackedIntoSeparateJar(module: JpsModule, layout: PluginLayout?): Boolean {
    val modulesWithExcludedModuleLibraries = layout?.modulesWithExcludedModuleLibraries ?: emptySet()
    return module.name !in modulesWithExcludedModuleLibraries &&
           getLibraryDependencies(module = module, withTests = false).any { it.libraryReference.parentReference is JpsModuleReference }
  }

  fun isTestPluginModule(moduleName: String): Boolean {
    return useTestSourceEnabled &&
           moduleName.contains(".test.") &&
           moduleName != "intellij.rider.test.framework" &&
           moduleName != "intellij.rider.test.api" &&
           moduleName != "intellij.rider.test.api.teamcity"
  }

  fun getPluginXmlContent(pluginModule: JpsModule): String {
    val moduleOutput = context.getModuleOutputDir(pluginModule, forTests = isTestPluginModule(pluginModule.name))
    if (moduleOutput.toString().endsWith(".jar")) {
      return getPluginXmlContentFromJar(moduleOutput)
    }

    val pluginXmlFile = moduleOutput.resolve("META-INF/plugin.xml")
    try {
      return Files.readString(pluginXmlFile)
    }
    catch (_: NoSuchFileException) {
      throw IllegalStateException("${pluginXmlFile.fileName} not found in ${pluginModule.name} module (file=$pluginXmlFile)")
    }
  }

  private fun getPluginXmlContentFromJar(moduleJar: Path): String {
    var pluginXmlContent: String? = null
    readZipFile(moduleJar) { name, data ->
      if (name == "META-INF/plugin.xml")
        pluginXmlContent = Charsets.UTF_8.decode(data()).toString()
    }

    return pluginXmlContent ?: throw IllegalStateException("META-INF/plugin.xml not found in ${moduleJar} module")
  }

  fun getPluginIdByModule(pluginModule: JpsModule): String {
    // it is ok to read the plugin descriptor with unresolved x-include as the ID should be specified at the root
    val root = readXmlAsModel(StringReader(getPluginXmlContent(pluginModule)))
    val element = root.getChild("id") ?: root.getChild("name") ?: throw IllegalStateException("Cannot find attribute id or name (module=$pluginModule)")
    return element.content!!
  }

  fun readPluginContentFromDescriptor(pluginModule: JpsModule, moduleOutputPatcher: ModuleOutputPatcher): Sequence<String> {
    return readPluginContentFromDescriptor(getResolvedPluginDescriptor(pluginModule, moduleOutputPatcher))
  }

  // plugin patcher should be executed before
  private fun getResolvedPluginDescriptor(pluginModule: JpsModule, moduleOutputPatcher: ModuleOutputPatcher): XmlElement {
    return moduleOutputPatcher.getPatchedPluginXmlIfExists(pluginModule.name)?.let { readXmlAsModel(it) } ?: readXmlAsModel(StringReader(getPluginXmlContent(pluginModule)))
  }

  // The x-include is not resolved. If the plugin.xml includes any files, the content from these included files will not be considered.
  fun readPluginIncompleteContentFromDescriptor(pluginModule: JpsModule): Sequence<String> {
    val pluginXml = context.findFileInModuleSources(pluginModule, "META-INF/plugin.xml") ?: return emptySequence()
    return readPluginContentFromDescriptor(readXmlAsModel(pluginXml))
  }

  private fun readPluginContentFromDescriptor(pluginDescriptor: XmlElement): Sequence<String> {
    return sequence {
      for (content in pluginDescriptor.children("content")) {
        for (module in content.children("module")) {
          val moduleName = module.attributes.get("name")?.takeIf { !it.contains('/') } ?: continue
          yield(moduleName)
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

  // cool.module.core has dependency on library cool-library.
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
      // intellij.space.kotlin depends on module intellij.space and both uses library org.apache.ivy
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
