// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.distributionContent.testFramework.deserializeContentData
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.pathString
import kotlin.io.path.readText

class ModuleBasedPluginLayoutProvider(
  private val project: JpsProject,
  runtimeModuleRepository: RuntimeModuleRepository,
  private val productRootModuleName: String,
  productMode: ProductMode,
  private val corePluginDescriptorPath: String,
) : PluginLayoutProvider {
  private val productModules: ProductModules
  private val mainModulesOfBundledPlugins: Set<String>

  init {
    val productRootModule = requireNotNull(project.findModuleByName(productRootModuleName)) { "Cannot find module '$productRootModuleName'" }
    val productModulesPath = requireNotNull(productRootModule.findProductionFile("META-INF/$productRootModuleName/product-modules.xml")) {
      "Cannot find product-modules.xml in '$productRootModuleName' module"
    }
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
    val rootEmbeddedModules = productModules.mainModuleGroup.includedModules
      .asSequence()
      .filter { it.loadingRule == RuntimeModuleLoadingRule.EMBEDDED }
      .map { it.moduleDescriptor }
    val embeddedModulesWithDependencies = LinkedHashSet<RuntimeModuleDescriptor>()
    fun collectDependencies(descriptor: RuntimeModuleDescriptor, result: MutableSet<RuntimeModuleDescriptor>) {
      if (result.add(descriptor)) {
        descriptor.dependencies.forEach { collectDependencies(it, result) }
      }
    }
    for (descriptor in rootEmbeddedModules) {
      collectDependencies(descriptor, embeddedModulesWithDependencies)
    }
    
    val mainGroupModules = embeddedModulesWithDependencies
      .asSequence()
      .map { it.moduleId.stringId }
      .filterNot { it.startsWith(RuntimeModuleId.LIB_NAME_PREFIX) }
      .mapNotNull { 
        project.findModuleByName(it)
      }
    val mainModule = requireNotNull(mainGroupModules.find { it.findProductionFile(corePluginDescriptorPath) != null }) {
      "Cannot find '$corePluginDescriptorPath' in the main module group of '$productRootModuleName'"
    }
    
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
    
    // Try to load plugin-content.yaml if it exists
    val contentDataPath = mainModule.findProductionFile("plugin-content.yaml")
    if (contentDataPath != null && contentDataPath.exists()) {
      val contentData = deserializeContentData(contentDataPath.readText())
      return toPluginLayoutDescription(
        entries = contentData,
        mainModuleName = mainModule.name,
        pluginDescriptorPath = "META-INF/plugin.xml",
        mainLibDir = "lib",
        jarsToIgnore = emptySet()
      )
    }
    
    // Fallback: just the main module
    return PluginLayoutDescription(
      mainJpsModule = mainModule.name,
      pluginDescriptorPath = "META-INF/plugin.xml",
      jpsModulesInClasspath = setOf(mainModule.name),
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = ""
}