// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.product.serialization.ResourceFileResolver
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

class ModuleBasedPluginLayoutProvider(
  private val project: JpsProject,
  private val runtimeModuleRepository: RuntimeModuleRepository,
  private val corePluginConfigurationModuleName: String,
  private val productRootModuleName: String,
  productMode: ProductMode,
  private val corePluginDescriptorPath: String,
) : PluginLayoutProvider {
  private val productModules: ProductModules
  private val mainModulesOfBundledPlugins: Set<String>
  private val embeddedModulesInBundledPlugins: Map<String, List<String>>

  init {
    val productRootModule = requireNotNull(project.findModuleByName(productRootModuleName)) { "Cannot find module '$productRootModuleName'" }
    val productModulesPath = requireNotNull(productRootModule.findProductionFile("META-INF/$productRootModuleName/product-modules.xml")) {
      "Cannot find product-modules.xml in '$productRootModuleName' module"
    }
    val resourceFileResolver = object : ResourceFileResolver {
      override fun readResourceFile(moduleId: RuntimeModuleId, relativePath: String): InputStream? {
        val module = project.findModuleByName(moduleId.name) ?: return null
        return module.findProductionFile(relativePath)?.inputStream()
      }
    }
    productModules = ProductModulesSerialization.loadProductModules(
      productModulesPath.inputStream(),
      productModulesPath.pathString,
      resourceFileResolver,
    )
    mainModulesOfBundledPlugins = productModules.bundledPluginDescriptorModules.mapTo(HashSet()) { it.name }
    embeddedModulesInBundledPlugins = productModules.bundledPluginDescriptorModules.associateBy(
      { it.name },
      { pluginDescriptorModule ->
        val header = runtimeModuleRepository.findBundledPluginHeader(pluginDescriptorModule) ?: return@associateBy emptyList()
        header.includedModules
          .filter { it.loadingRule == RuntimeModuleLoadingRule.EMBEDDED && !it.moduleId.namespace.endsWith(RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX)
                    && !it.moduleId.namespace.endsWith(RuntimeModuleId.LEGACY_JPS_MODULE_TESTS_NAMESPACE_SUFFIX) }
          .map { it.moduleId.name }
      })
  }

  private fun JpsModule.findProductionFile(relativePath: String): Path? = JpsJavaExtensionService.getInstance().findSourceFileInProductionRoots(this, relativePath)

  override fun loadCorePluginLayout(): PluginLayoutDescription {
    val corePluginHeader = runtimeModuleRepository.findBundledPluginHeader(RuntimeModuleId.legacyJpsModule(corePluginConfigurationModuleName))
                           ?: error("Cannot find core plugin header for module '$corePluginConfigurationModuleName'")
    val embeddedModules = corePluginHeader.includedModules
      .asSequence()
      .filter { it.loadingRule == RuntimeModuleLoadingRule.EMBEDDED }
      .map { it.moduleId }
      .filterNot { it.namespace.endsWith(RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX) }
      .mapNotNull {
        project.findModuleByName(it.name)
      }

    val mainModule = requireNotNull(embeddedModules.find { it.findProductionFile(corePluginDescriptorPath) != null }) {
      "Cannot find '$corePluginDescriptorPath' in the main module group of '$productRootModuleName'"
    }

    return PluginLayoutDescription(
      mainJpsModule = mainModule.name,
      pluginDescriptorPath = corePluginDescriptorPath,
      jpsModulesInClasspath = embeddedModules.mapTo(LinkedHashSet()) { it.name },
    )
  }

  override fun loadMainModulesOfBundledPlugins(): List<String> {
    return mainModulesOfBundledPlugins.toList()
  }

  override fun loadPluginLayout(mainModule: JpsModule): PluginLayoutDescription? {
    if (mainModule.name !in mainModulesOfBundledPlugins) {
      return null
    }

    val embeddedModules = embeddedModulesInBundledPlugins[mainModule.name]?.toSet() ?: emptySet()
    return PluginLayoutDescription(
      mainJpsModule = mainModule.name,
      pluginDescriptorPath = "META-INF/plugin.xml",
      jpsModulesInClasspath = embeddedModules,
    )
  }

  override val messageDescribingHowToUpdateLayoutData: String
    get() = ""
}