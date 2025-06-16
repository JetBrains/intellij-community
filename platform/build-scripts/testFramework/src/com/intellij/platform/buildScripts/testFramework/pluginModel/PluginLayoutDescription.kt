// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.distributionContent.testFramework.FileEntry
import com.intellij.platform.distributionContent.testFramework.deserializeContentData
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

fun createLayoutProviderByContentYamlFiles(ideContentYamlPath: Path, mainModuleOfCorePlugin: String, corePluginDescriptorPath: String, nameOfTestWhichGeneratesFiles: String): PluginLayoutProvider {
  return YamlFileBasedPluginLayoutProvider(ideContentYamlPath, mainModuleOfCorePlugin, corePluginDescriptorPath, nameOfTestWhichGeneratesFiles = nameOfTestWhichGeneratesFiles) 
}

private class YamlFileBasedPluginLayoutProvider(
  private val ideContentYamlPath: Path, 
  private val mainModuleOfCorePlugin: String,
  private val corePluginDescriptorPath: String,
  private val nameOfTestWhichGeneratesFiles: String,
) : PluginLayoutProvider {
  private val ideContentData by lazy {
    deserializeContentData(ideContentYamlPath.readText())
  }
  
  override fun loadCorePluginLayout(): PluginLayoutDescription {
    return ideContentData.toPluginLayoutDescription(
      mainModuleName = mainModuleOfCorePlugin,
      pluginDescriptorPath = corePluginDescriptorPath,
      mainLibDir = "dist.all/lib",
      jarsToIgnore = setOf("dist.all/lib/testFramework.jar")
    )
  }

  override fun loadMainModulesOfBundledPlugins(): List<String> {
    return ideContentData.flatMap { it.bundled }
  }

  override fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription? {
    val contentRootUrl = mainModule.contentRootsList.urls.firstOrNull() ?: return null
    val pluginContentPath = "plugin-content.yaml"
    val contentDataPath = JpsPathUtil.urlToNioPath(contentRootUrl).resolve(pluginContentPath)
    if (!contentDataPath.exists()) return null
    val pluginDescriptorPath = "META-INF/plugin.xml"
    if (JpsJavaExtensionService.getInstance().findSourceFileInProductionRoots(mainModule, pluginDescriptorPath) == null) {
      throw PluginModuleConfigurationError(mainModule.name, """
        '$pluginDescriptorPath' file is not found in source and resource roots of module '"${mainModule.name}', but '$pluginContentPath' is present in it.
        If '${mainModule.name}' is not the main module of a plugin anymore, delete '$pluginContentPath' to avoid confusion. 
      """.trimIndent())
    }
    
    val contentData = deserializeContentData(contentDataPath.readText())
    return contentData.toPluginLayoutDescription(
      mainModuleName = mainModule.name,
      pluginDescriptorPath = pluginDescriptorPath,
      mainLibDir = "lib",
      jarsToIgnore = emptySet()
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = "Note that the test uses the data from *content.yaml files, so if you changed the layouts, run '$nameOfTestWhichGeneratesFiles' to make sure that they are up-to-date."
}

private fun List<FileEntry>.toPluginLayoutDescription(mainModuleName: String, pluginDescriptorPath: String, mainLibDir: String, jarsToIgnore: Set<String>): PluginLayoutDescription {
  return PluginLayoutDescription(
    mainJpsModule = mainModuleName,
    pluginDescriptorPath = pluginDescriptorPath,
    jpsModulesInClasspath = 
      filter { it.name.substringBeforeLast('/', "") == mainLibDir && it.name !in jarsToIgnore }
        .flatMapTo(LinkedHashSet()) { it.modules.map { it.name } }
  )
}
