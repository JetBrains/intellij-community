// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.buildScripts.testFramework.distributionContent.ParsedContentReport
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.PluginContentReport
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.PLUGIN_XML_RELATIVE_PATH
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

/**
 * Provides information about layout of plugins for [PluginDependenciesValidator].
 */
interface PluginLayoutProvider {
  fun loadCorePluginLayout(): PluginLayoutDescription
  fun loadMainModulesOfBundledPlugins(): List<String>
  fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription?
  fun findModuleLibraryRoots(moduleName: String): List<Path> = emptyList()
  val messageDescribingHowToUpdateLayoutData: String
}

data class PluginLayoutDescription(
  val mainJpsModule: String,
  /**
   * Path to the plugin descriptor file relative to the resource root.
   */
  val pluginDescriptorPath: String,
  /**
   * Names of JPS modules which are included in the classpath of the main plugin module.
   */
  val jpsModulesInClasspath: Set<String>,
  /**
   * Resolved roots of libraries which are included in the classpath of the main plugin module.
   */
  val libraryRootsInClasspath: List<Path> = emptyList(),
)

fun createLayoutProviderByContentReport(
  content: ParsedContentReport,
  mainModuleOfCorePlugin: String,
  corePluginDescriptorPath: String,
  outputProvider: ModuleOutputProvider,
): PluginLayoutProvider {
  return ContentReportBasedPluginLayoutProvider(
    content = content,
    mainModuleOfCorePlugin = mainModuleOfCorePlugin,
    corePluginDescriptorPath = corePluginDescriptorPath,
    outputProvider = outputProvider,
    mainModulesWithPluginDescriptor = collectMainModulesWithPluginDescriptor(content = content, outputProvider = outputProvider),
  )
}

private fun collectMainModulesWithPluginDescriptor(
  content: ParsedContentReport,
  outputProvider: ModuleOutputProvider,
): Set<String> {
  val mainModules = LinkedHashSet<String>()
  for (item in content.bundled + content.nonBundled) {
    mainModules.add(item.mainModule)
  }

  return mainModules.mapConcurrent { mainModule ->
    val module = outputProvider.findModule(mainModule) ?: return@mapConcurrent null
    val descriptorContent = outputProvider.readFileContentFromModuleOutput(
      module = module,
      relativePath = PLUGIN_XML_RELATIVE_PATH,
      forTests = false,
    )
    mainModule.takeIf { descriptorContent != null }
  }.filterNotNullTo(HashSet())
}

private class ContentReportBasedPluginLayoutProvider(
  private val content: ParsedContentReport,
  private val mainModuleOfCorePlugin: String,
  private val corePluginDescriptorPath: String,
  private val outputProvider: ModuleOutputProvider,
  private val mainModulesWithPluginDescriptor: Set<String>,
) : PluginLayoutProvider {
  override fun findModuleLibraryRoots(moduleName: String): List<Path> {
    val module = outputProvider.findModule(moduleName) ?: return emptyList()
    return module.libraryCollection.libraries.flatMap { library ->
      outputProvider.findLibraryRoots(library.name, moduleLibraryModuleName = moduleName)
    }
  }

  private val mainModulesOfBundledPlugins by lazy {
    content.bundled.mapTo(LinkedHashSet()) { it.mainModule }
  }

  private val mainModuleToPluginContent by lazy {
    val result = LinkedHashMap<String, PluginContentReport>()
    for (item in content.bundled + content.nonBundled) {
      result.putIfAbsent(item.mainModule, item)
    }
    result
  }

  private val mergedContentDataForEmbeddedModules by lazy {
    content.platform + content.productModules.flatMap { productModule ->
      productModule.content.map { fileEntry ->
        if (fileEntry.name == "<file>") {
          fileEntry.copy(name = "dist.all/lib/${productModule.mainModule}.jar")
        }
        else {
          fileEntry
        }
      }
    }
  }

  override fun loadCorePluginLayout(): PluginLayoutDescription {
    return toPluginLayoutDescription(
      entries = mergedContentDataForEmbeddedModules,
      mainModuleName = mainModuleOfCorePlugin,
      pluginDescriptorPath = corePluginDescriptorPath,
      mainLibDir = "dist.all/lib",
      jarsToIgnore = setOf("dist.all/lib/testFramework.jar"),
      libraryRootResolver = outputProvider::findLibraryRoots,
    )
  }

  override fun loadMainModulesOfBundledPlugins(): List<String> {
    return mainModulesOfBundledPlugins.toList()
  }

  override fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription? {
    val pluginContent = mainModuleToPluginContent[mainModule.name] ?: return null
    if (mainModule.name !in mainModulesWithPluginDescriptor) {
      throw PluginModuleConfigurationError(
        pluginModelModuleName = mainModule.name,
        errorMessage = """
                '$PLUGIN_XML_RELATIVE_PATH' file is not found in production output of module '${mainModule.name}'.
                The module is present in the content report; if it is not the main module of a plugin anymore,
                update the product layout to avoid confusion.
              """.trimIndent(),
      )
    }

    return toPluginLayoutDescription(
      entries = pluginContent.content,
      mainModuleName = mainModule.name,
      pluginDescriptorPath = PLUGIN_XML_RELATIVE_PATH,
      mainLibDir = "lib",
      jarsToIgnore = emptySet(),
      libraryRootResolver = outputProvider::findLibraryRoots,
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = "Note that the validation uses the generated content report from AllProductsPackagingTest, " +
            "so content snapshots are checked by the same test."
}

internal fun toPluginLayoutDescription(
  entries: List<FileEntry>,
  mainModuleName: String,
  pluginDescriptorPath: String,
  mainLibDir: String,
  jarsToIgnore: Set<String>,
  libraryRootResolver: (libraryName: String, moduleLibraryModuleName: String?) -> List<Path> = { _, _ -> emptyList() },
): PluginLayoutDescription {
  val libEntries = entries
    .asSequence()
    .filter { it.name.substringBeforeLast('/', "") == mainLibDir && it.name !in jarsToIgnore }
    .toList()
  val projectLibraries = libEntries
    .asSequence()
    .flatMap { entry ->
      val projectLibraryNames = entry.projectLibraries.asSequence().map { it.name }
      val fileProjectLibraryName = listOfNotNull(entry.library.takeIf { entry.module == null }).asSequence()
      projectLibraryNames + fileProjectLibraryName
    }
    .toCollection(LinkedHashSet())
  val moduleLibraries = libEntries
    .asSequence()
    .flatMap { entry ->
      val mergedLibraries = (entry.modules + entry.contentModules).asSequence()
        .flatMap { module -> module.libraries.keys.asSequence().map { it to module.name } }
      val fileLibrary = entry.library?.let { library -> entry.module?.let { module -> library to module } }
      mergedLibraries + listOfNotNull(fileLibrary).asSequence()
    }
    .filterNot { (library, _) -> library.endsWith(".jar") }
    .toCollection(LinkedHashSet())

  return PluginLayoutDescription(
    mainJpsModule = mainModuleName,
    pluginDescriptorPath = pluginDescriptorPath,
    jpsModulesInClasspath = libEntries
      .flatMapTo(LinkedHashSet()) { entry -> entry.modules.map { it.name } },
    libraryRootsInClasspath = projectLibraries.flatMap { libraryRootResolver(it, null) } +
                              moduleLibraries.flatMap { (libraryName, moduleName) -> libraryRootResolver(libraryName, moduleName) },
  )
}
