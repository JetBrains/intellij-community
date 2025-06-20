// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.openapi.application.PathManager
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

fun createLayoutProviderForProductWithModuleBasedLoader(
  project: JpsProject,
  productRootModuleName: String,
  productMode: ProductMode,
  corePluginDescriptorPath: String,
): PluginLayoutProvider {
  return ModuleBasedPluginLayoutProvider(project, productRootModuleName, productMode, corePluginDescriptorPath)
}

private class ModuleBasedPluginLayoutProvider(
  private val project: JpsProject,
  private val productRootModuleName: String,
  productMode: ProductMode,
  private val corePluginDescriptorPath: String,
) : PluginLayoutProvider {
  private val productModules: ProductModules
  private val mainModulesOfBundledPlugins: Set<String>

  init {
    val projectOutputDir = JpsPathUtil.urlToNioPath(JpsJavaExtensionService.getInstance().getProjectExtension(project)!!.outputUrl)
    val runtimeModuleRepositoryPath: Path
    val mapping = PathManager.getArchivedCompiledClassesMapping()
    if (mapping != null) {
      runtimeModuleRepositoryPath = mapping.entries.firstOrNull { it.key.endsWith("module-descriptors.jar") }?.value?.let { Path.of(it) }
                                    ?: error("Cannot find module-descriptors.jar in the mapping")
    }
    else {
      runtimeModuleRepositoryPath = projectOutputDir.resolve("module-descriptors.jar")
    }
    assert(runtimeModuleRepositoryPath.exists()) {
      """|$runtimeModuleRepositoryPath doesn't exists; it should be generated during compilation by DevKit plugin for JPS process,
         |so check that DevKit plugin is enabled.""".trimMargin()
    }
    val data = RuntimeModuleRepositorySerialization.loadFromJar(runtimeModuleRepositoryPath)
    val runtimeModuleRepository = RuntimeModuleRepositoryImpl(runtimeModuleRepositoryPath, data)
    val productRootModule = project.findModuleByName(productRootModuleName) ?: error("Cannot find module '$productRootModuleName'")
    val productModulesPath = productRootModule.findProductionFile("META-INF/$productRootModuleName/product-modules.xml")
                             ?: error("Cannot find product-modules.xml in '$productRootModuleName' module")
    val resourceFileResolver = object : ResourceFileResolver {
      override fun readResourceFile(moduleId: RuntimeModuleId, relativePath: String): InputStream? {
        val module = project.findModuleByName(moduleId.stringId) ?: return null
        return module.findProductionFile(relativePath)?.inputStream()
      }
    }
    productModules = ProductModulesSerialization.loadProductModules(
      productModulesPath.inputStream(), 
      productModulesPath.pathString,
      productMode,
      runtimeModuleRepository,
      resourceFileResolver,
    )
    mainModulesOfBundledPlugins = productModules.bundledPluginModuleGroups.mapTo(HashSet()) { it.mainModule.moduleId.stringId }
  }

  private fun JpsModule.findProductionFile(relativePath: String): Path? = JpsJavaExtensionService.getInstance().findSourceFileInProductionRoots(this, relativePath)

  override fun loadCorePluginLayout(): PluginLayoutDescription {
    val mainGroupModules = productModules.mainModuleGroup.includedModules
      .asSequence()
      .map { it.moduleDescriptor.moduleId.stringId }
      .filterNot { it.startsWith(RuntimeModuleId.LIB_NAME_PREFIX) }
      .mapNotNull { 
        project.findModuleByName(it)
      }
    val mainModule = mainGroupModules.find { 
      it.findProductionFile(corePluginDescriptorPath) != null
    } ?: error("Cannot find '$corePluginDescriptorPath' in the main module group of '$productRootModuleName'")
    
    return PluginLayoutDescription(
      mainJpsModule = mainModule.name,
      pluginDescriptorPath = corePluginDescriptorPath,
      jpsModulesInClasspath = mainGroupModules.mapTo(LinkedHashSet()) { it.name },
    )
  }

  override fun loadMainModulesOfBundledPlugins(): List<String> {
    return mainModulesOfBundledPlugins.toList()
  }

  override fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription? {
    if (mainModule.name !in mainModulesOfBundledPlugins) {
      return null
    }
    
    return PluginLayoutDescription(
      mainJpsModule = mainModule.name,
      pluginDescriptorPath = "META-INF/plugin.xml",
      jpsModulesInClasspath = setOf(mainModule.name),
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = ""
}