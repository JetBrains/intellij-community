// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.buildScripts.testFramework.distributionContent.ParsedContentReport
import com.intellij.platform.distributionContent.FileEntry
import com.intellij.platform.distributionContent.PluginContentReport
import com.intellij.platform.distributionContent.deserializeContentData
import com.intellij.platform.pluginSystem.testFramework.MissingModuleSetDescriptorException
import com.intellij.platform.pluginSystem.testFramework.buildStalePackagingDataMessage
import com.intellij.platform.pluginSystem.testFramework.resolveModuleSet
import kotlinx.coroutines.Dispatchers
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.mapConcurrent
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
  /**
   * Resolved roots of libraries which are included in the classpath of the main plugin module.
   */
  val libraryRootsInClasspath: List<Path> = emptyList(),
)

/**
 * Creates a description of plugins using data from product and plugins' content.yaml files.
 */
fun createLayoutProviderByContentYamlFiles(
  contentYamlPath: Path,
  projectHome: Path,
  mainModuleOfCorePlugin: String,
  corePluginDescriptorPath: String,
  nameOfTestWhichGeneratesFiles: String,
  project: JpsProject,
): PluginLayoutProvider {
  return YamlFileBasedPluginLayoutProvider(
    contentYamlPath = contentYamlPath,
    mainModuleOfCorePlugin = mainModuleOfCorePlugin,
    corePluginDescriptorPath = corePluginDescriptorPath,
    nameOfTestWhichGeneratesFiles = nameOfTestWhichGeneratesFiles,
    project = project,
    projectHome = projectHome,
  )
}

suspend fun createLayoutProviderByContentReport(
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

private suspend fun collectMainModulesWithPluginDescriptor(
  content: ParsedContentReport,
  outputProvider: ModuleOutputProvider,
): Set<String> {
  val mainModules = LinkedHashSet<String>()
  for (item in content.bundled + content.nonBundled) {
    mainModules.add(item.mainModule)
  }

  return mainModules.mapConcurrent(workerDispatcher = Dispatchers.IO) { mainModule ->
    val module = outputProvider.findModule(mainModule) ?: return@mapConcurrent null
    val descriptorContent = outputProvider.readFileContentFromModuleOutput(
      module = module,
      relativePath = "META-INF/plugin.xml",
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
    val pluginDescriptorPath = "META-INF/plugin.xml"
    if (mainModule.name !in mainModulesWithPluginDescriptor) {
      throw PluginModuleConfigurationError(
        pluginModelModuleName = mainModule.name,
        errorMessage = """
                '$pluginDescriptorPath' file is not found in production output of module '${mainModule.name}'.
                The module is present in the content report; if it is not the main module of a plugin anymore,
                update the product layout to avoid confusion. 
              """.trimIndent(),
      )
    }

    return toPluginLayoutDescription(
      entries = pluginContent.content,
      mainModuleName = mainModule.name,
      pluginDescriptorPath = pluginDescriptorPath,
      mainLibDir = "lib",
      jarsToIgnore = emptySet(),
      libraryRootResolver = outputProvider::findLibraryRoots,
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = "Note that the validation uses the generated content report from AllProductsPackagingTest, " +
            "so content snapshots are checked by the same test."
}

private class YamlFileBasedPluginLayoutProvider(
  private val contentYamlPath: Path,
  private val mainModuleOfCorePlugin: String,
  private val corePluginDescriptorPath: String,
  private val nameOfTestWhichGeneratesFiles: String,
  private val project: JpsProject,
  private val projectHome: Path,
) : PluginLayoutProvider {
  private val contentData by lazy {
    deserializeContentData(contentYamlPath.readText())
  }
  private val mainModulesOfBundledPlugins by lazy {
    contentData.asSequence().flatMap { it.bundled }.mapTo(LinkedHashSet()) { it.mainModule }
  }
  private val mainModulesOfNonBundledPlugins by lazy {
    contentData.asSequence().flatMap { it.nonBundled }.mapTo(LinkedHashSet()) { it.mainModule }
  }

  private val mergedContentDataForEmbeddedModules by lazy {
    loadMergedDataForEmbeddedModules()
  }

  private fun loadMergedDataForEmbeddedModules(): List<FileEntry> {
    val baseEntries = contentData.toMutableList()

    // Collect productModules and productEmbeddedModules separately, expanding module sets
    val productModuleNames: List<String>
    val productEmbeddedModuleNames: List<String>
    try {
      productModuleNames = contentData
        .asSequence()
        .flatMap { it.productModules }
        .flatMap { moduleName -> resolveModuleSet(moduleName, embeddedOnly = true, projectHome) }
        .distinct()
        .toList()

      productEmbeddedModuleNames = contentData
        .asSequence()
        .flatMap { it.productEmbeddedModules }
        .flatMap { moduleName -> resolveModuleSet(moduleName, embeddedOnly = true, projectHome) }
        .distinct()
        .toList()
    }
    catch (e: MissingModuleSetDescriptorException) {
      throw PluginModuleConfigurationError(
        pluginModelModuleName = mainModuleOfCorePlugin,
        errorMessage = e.buildStalePackagingDataMessage(
          contentYamlPath = contentYamlPath,
          projectRoot = projectHome,
          generatorTestName = nameOfTestWhichGeneratesFiles,
        ),
      )
    }

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
    if (mainModule.name !in mainModulesOfBundledPlugins && mainModule.name !in mainModulesOfNonBundledPlugins) {
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
    .flatMap { it.modules + it.contentModules }
    .flatMap { module -> module.libraries.keys.asSequence().filterNot { it.endsWith(".jar") }.map { it to module.name } }
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
