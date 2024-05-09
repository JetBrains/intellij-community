// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.intellij.build

import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ModuleOutputPatcher
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.*
import java.io.StringReader
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

// production-only - JpsJavaClasspathKind.PRODUCTION_RUNTIME
internal class JarPackagerDependencyHelper(private val context: BuildContext) {
  private val javaExtensionService = JpsJavaExtensionService.getInstance()

  private val libraryCache = ConcurrentHashMap<JpsModule, List<JpsLibraryDependency>>()

  fun getModuleDependencies(moduleName: String): Sequence<String> {
    return getModuleDependencies(context.findRequiredModule(moduleName)).map { it.moduleReference.moduleName }
  }

  fun isPluginModulePackedIntoSeparateJar(module: JpsModule, layout: PluginLayout?): Boolean {
    return !(layout?.modulesWithExcludedModuleLibraries ?: emptySet()).contains(module.name) &&
           getLibraryDependencies(module).any { it.libraryReference.parentReference is JpsModuleReference }
  }

  fun getPluginXmlContent(pluginModule: JpsModule): String {
    val moduleOutput = context.getModuleOutputDir(pluginModule)
    val pluginXmlFile = moduleOutput.resolve("META-INF/plugin.xml")
    try {
      return Files.readString(pluginXmlFile)
    }
    catch (e: NoSuchFileException) {
      throw IllegalStateException("${pluginXmlFile.fileName} not found in ${pluginModule.name} module (file=$pluginXmlFile)")
    }
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
        if (element is JpsModuleDependency && isProductionRuntime(element)) {
          yield(element)
        }
      }
    }
  }

  fun getLibraryDependencies(module: JpsModule): List<JpsLibraryDependency> {
    return libraryCache.computeIfAbsent(module) {
      val result = mutableListOf<JpsLibraryDependency>()
      for (element in module.dependenciesList.dependencies) {
        if (isProductionRuntime(element) && element is JpsLibraryDependency) {
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
  fun hasLibraryInDependencyChainOfModuleDependencies(dependentModule: JpsModule, libraryName: String, siblings: Collection<ModuleItem>): Boolean {
    val prefix = dependentModule.name.let { it.substring(0, it.lastIndexOf('.') + 1) }
    for (dependency in getModuleDependencies(dependentModule)) {
      val moduleName = dependency.moduleReference.moduleName
      if (moduleName.startsWith(prefix) &&
          siblings.none { it.moduleName == moduleName } &&
          getLibraryDependencies(dependency.module ?: continue).any { it.libraryReference.libraryName == libraryName }) {
        return true
      }
    }
    return false
  }

  private fun isProductionRuntime(element: JpsDependencyElement): Boolean {
    return javaExtensionService.getDependencyExtension(element)?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) == true
  }
}
