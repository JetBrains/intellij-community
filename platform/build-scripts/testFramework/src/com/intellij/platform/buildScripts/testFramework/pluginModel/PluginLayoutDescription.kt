// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.deserializeContentData
import com.intellij.platform.pluginSystem.testFramework.resolveModuleSet
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Provides information about layout of plugins for [PluginDependenciesValidator].
 */
interface PluginLayoutProvider {
  fun loadCorePluginLayout(): PluginLayoutDescription
  fun loadMainModulesOfBundledPlugins(): List<String>
  fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription?
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
)

/**
 * Creates a description of plugins using data from IDE and plugins' content.yaml files.
 * @param includeOnlyPluginsSpecifiedInYamlFile if `true`, checks only bundled and non-bundled plugins specified in [ideContentYamlPath];
 *                                              if `false`, checks all plugins which has `plugin-content.yaml` file in their content roots.
 */
fun createLayoutProviderByContentYamlFiles(
  ideContentYamlPath: Path,
  ultimateHome: Path,
  mainModuleOfCorePlugin: String,
  corePluginDescriptorPath: String,
  nameOfTestWhichGeneratesFiles: String,
  includeOnlyPluginsSpecifiedInYamlFile: Boolean = false,
  project: JpsProject,
): PluginLayoutProvider {
  return YamlFileBasedPluginLayoutProvider(
    ideContentYamlPath = ideContentYamlPath,
    mainModuleOfCorePlugin = mainModuleOfCorePlugin,
    corePluginDescriptorPath = corePluginDescriptorPath,
    nameOfTestWhichGeneratesFiles = nameOfTestWhichGeneratesFiles,
    includeOnlyPluginsSpecifiedInYamlFile = includeOnlyPluginsSpecifiedInYamlFile,
    project = project,
    ultimateHome = ultimateHome,
  )
}

private class YamlFileBasedPluginLayoutProvider(
  private val ideContentYamlPath: Path,
  private val mainModuleOfCorePlugin: String,
  private val corePluginDescriptorPath: String,
  private val nameOfTestWhichGeneratesFiles: String,
  private val includeOnlyPluginsSpecifiedInYamlFile: Boolean,
  private val project: JpsProject,
  private val ultimateHome: Path,
) : PluginLayoutProvider {
  private val ideContentData by lazy {
    deserializeContentData(ideContentYamlPath.readText())
  }
  private val mainModulesOfBundledPlugins by lazy {
    ideContentData.asSequence().flatMap { it.bundled }.mapTo(LinkedHashSet()) { it.mainModule }
  }
  private val mainModulesOfNonBundledPlugins by lazy {
    ideContentData.asSequence().flatMap { it.nonBundled }.mapTo(LinkedHashSet()) { it.mainModule }
  }

  private val mergedContentDataForEmbeddedModules by lazy {
    loadMergedDataForEmbeddedModules()
  }

  private fun loadMergedDataForEmbeddedModules(): List<FileEntry> {
    val baseEntries = ideContentData.toMutableList()

    // Collect productModules and productEmbeddedModules separately, expanding module sets
    val productModuleNames = ideContentData
      .asSequence()
      .flatMap { it.productModules }
      .flatMap { moduleName -> resolveModuleSet(moduleName, embeddedOnly = true, ultimateHome) }
      .distinct()

    val productEmbeddedModuleNames = ideContentData
      .asSequence()
      .flatMap { it.productEmbeddedModules }
      .flatMap { moduleName -> resolveModuleSet(moduleName, embeddedOnly = true, ultimateHome) }
      .distinct()

    for (moduleName in (productModuleNames + productEmbeddedModuleNames)) {
      loadAndMergeModuleContent(moduleName, baseEntries)
    }

    return baseEntries
  }

  private fun loadAndMergeModuleContent(moduleName: String, baseEntries: MutableList<FileEntry>) {
    val module = project.findModuleByName(moduleName) ?: return
    val contentRootUrl = module.contentRootsList.urls.firstOrNull() ?: return
    val moduleContentPath = JpsPathUtil.urlToNioPath(contentRootUrl).resolve("module-content.yaml")

    if (!moduleContentPath.exists()) {
      return
    }

    val moduleEntries = deserializeContentData(moduleContentPath.readText())

    // replace <file> placeholder with actual jar path
    for (entry in moduleEntries) {
      if (entry.name == "<file>") {
        baseEntries.add(entry.copy(name = "dist.all/lib/$moduleName.jar"))
      }
      else {
        baseEntries.add(entry)
      }
    }
  }

  override fun loadCorePluginLayout(): PluginLayoutDescription {
    return toPluginLayoutDescription(
      entries = mergedContentDataForEmbeddedModules,
      mainModuleName = mainModuleOfCorePlugin,
      pluginDescriptorPath = corePluginDescriptorPath,
      mainLibDir = "dist.all/lib",
      jarsToIgnore = setOf("dist.all/lib/testFramework.jar")
    )
  }

  override fun loadMainModulesOfBundledPlugins(): List<String> {
    return mainModulesOfBundledPlugins.toList()
  }

  override fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription? {
    if (includeOnlyPluginsSpecifiedInYamlFile && mainModule.name !in mainModulesOfBundledPlugins && mainModule.name !in mainModulesOfNonBundledPlugins) {
      return null
    }
    val contentRootUrl = mainModule.contentRootsList.urls.firstOrNull() ?: return null
    val pluginContentPath = "plugin-content.yaml"
    val contentDataPath = JpsPathUtil.urlToNioPath(contentRootUrl).resolve(pluginContentPath)
    if (!contentDataPath.exists()) return null
    val pluginDescriptorPath = "META-INF/plugin.xml"
    if (JpsJavaExtensionService.getInstance().findSourceFileInProductionRoots(mainModule, pluginDescriptorPath) == null) {
      throw PluginModuleConfigurationError(
        pluginModelModuleName = mainModule.name,
        errorMessage = """
                '$pluginDescriptorPath' file is not found in source and resource roots of module '"${mainModule.name}', but '$pluginContentPath' is present in it.
                If '${mainModule.name}' is not the main module of a plugin anymore, delete '$pluginContentPath' to avoid confusion. 
              """.trimIndent(),
      )
    }

    val contentData = deserializeContentData(contentDataPath.readText())
    return toPluginLayoutDescription(
      entries = contentData,
      mainModuleName = mainModule.name,
      pluginDescriptorPath = pluginDescriptorPath,
      mainLibDir = "lib",
      jarsToIgnore = emptySet()
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = "Note that the test uses the data from *content.yaml files, so if you changed the layouts, run '$nameOfTestWhichGeneratesFiles' to make sure that they are up-to-date."
}

internal fun toPluginLayoutDescription(
  entries: List<FileEntry>,
  mainModuleName: String,
  pluginDescriptorPath: String,
  mainLibDir: String,
  jarsToIgnore: Set<String>,
): PluginLayoutDescription {
  return PluginLayoutDescription(
    mainJpsModule = mainModuleName,
    pluginDescriptorPath = pluginDescriptorPath,
    jpsModulesInClasspath = entries
      .asSequence()
      .filter { it.name.substringBeforeLast('/', "") == mainLibDir && it.name !in jarsToIgnore }
      .flatMapTo(LinkedHashSet()) { entry -> entry.modules.map { it.name } }
  )
}
