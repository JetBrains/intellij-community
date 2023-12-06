// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.*
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.FList
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.MODULE_DESCRIPTORS_JAR_PATH
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.getOsAndArchSpecificDistDirectory
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Checks that runtime module descriptors in the product distribution are valid.
 */
@OptIn(ExperimentalPathApi::class)
class RuntimeModuleRepositoryChecker private constructor(
  private val commonDistPath: Path,
  private val osSpecificDistPath: Path?,
  private val currentMode: ProductMode,
  private val context: BuildContext,
): AutoCloseable {
  private val descriptorsJarFile: Path
  private val repository: RuntimeModuleRepository
  private val osSpecificFilePaths: List<Path>
  init {
    if (osSpecificDistPath != null) {
      /* Module repository stores relative paths to JARs, but here JARs are distributed between commonDistPath and osSpecificDistPath. 
         Copying them to a single directory like in production may take considerable time, so for now OS-specific files are moved to
         the common directory before starting the check and moved back when the check finishes. */
      osSpecificFilePaths = osSpecificDistPath.walk().map { osSpecificDistPath.relativize(it) }.toList()
      osSpecificFilePaths.forEach {
        val target = commonDistPath.resolve(it)
        target.parent.createDirectories()
        osSpecificDistPath.resolve(it).moveTo(target)
      }
    }
    else {
      osSpecificFilePaths = emptyList()
    }
    descriptorsJarFile = commonDistPath.resolve(MODULE_DESCRIPTORS_JAR_PATH)
    repository = RuntimeModuleRepository.create(descriptorsJarFile)
  }
  
  companion object {
    fun checkProductModules(productModulesModule: String, currentMode: ProductMode, context: BuildContext, softly: SoftAssertions) {
      createCheckers(currentMode, context).forEach { 
        it().use { checker ->
          checker.checkProductModules(productModulesModule, softly)
        }
      }
    }

    /**
     * Verifies that subset of the current product described by product-modules.xml file from [productModulesModule] can be loaded as a
     * separate product: JARs referenced from its modules must not include resources from modules not included to the product, or they are
     * split by packages in a way that the class-loader may load relevant classes only.
     */
    fun checkIntegrityOfEmbeddedProduct(productModulesModule: String, currentMode: ProductMode, context: BuildContext, softly: SoftAssertions) {
      createCheckers(currentMode, context).forEach {
        it().use { checker ->
          checker.checkIntegrityOfEmbeddedProduct(productModulesModule, softly)
        }
      }
    }

    private fun createCheckers(currentMode: ProductMode, context: BuildContext): List<() -> RuntimeModuleRepositoryChecker> {
      val commonDistPath = context.paths.distAllDir 
      if (commonDistPath.resolve(MODULE_DESCRIPTORS_JAR_PATH).exists()) {
        return listOf { RuntimeModuleRepositoryChecker(commonDistPath, null, currentMode, context) }
      }
      return SUPPORTED_DISTRIBUTIONS
        .mapNotNull { distribution ->
          val osSpecificDistPath = getOsAndArchSpecificDistDirectory(distribution.os, distribution.arch, context)
          if (osSpecificDistPath.resolve(MODULE_DESCRIPTORS_JAR_PATH).exists()) {
            { RuntimeModuleRepositoryChecker(commonDistPath, osSpecificDistPath, currentMode, context) }
          }
          else null
        }
    }
  }
  private val descriptors by lazy { RuntimeModuleRepositorySerialization.loadFromJar(descriptorsJarFile) }

  private fun checkProductModules(productModulesModule: String, softly: SoftAssertions) {
    try {
      val productModules = loadProductModules(productModulesModule)
      val allDependencies = HashMap<RuntimeModuleId, FList<String>>()
      productModules.mainModuleGroup.includedModules.forEach { 
        repository.collectDependencies(it.moduleDescriptor, FList.emptyList(), allDependencies)
      }
      productModules.bundledPluginModuleGroups.forEach { group ->
        group.includedModules.forEach { 
          repository.collectDependencies(it.moduleDescriptor, FList.emptyList(), allDependencies)
        }
      }
    }
    catch (e: MalformedRepositoryException) { 
      softly.collectAssertionError(AssertionError("Failed to load product-modules.xml for $descriptorsJarFile: $e", e))
    }
  }

  private fun checkIntegrityOfEmbeddedProduct(productModulesModule: String, softly: SoftAssertions) {
    val productModules = loadProductModules(productModulesModule)

    val allProductModules = LinkedHashMap<RuntimeModuleId, FList<String>>()
    allProductModules[RuntimeModuleId.module("intellij.platform.bootstrap")] = FList.singleton("bootstrap")
    val mainModuleGroupPath = FList.singleton("main module group")
    productModules.mainModuleGroup.includedModules.forEach { mainModule ->
      repository.collectDependencies(mainModule.moduleDescriptor, mainModuleGroupPath, allProductModules)
    }
    productModules.bundledPluginModuleGroups.forEach { group ->
      if (group.includedModules.isEmpty()) {
        softly.collectAssertionError(AssertionError("""
           |No modules from '$group' are included in a product running in '${currentMode.id}' mode, so corresponding plugin won't be loaded.
           |Probably it indicates that some incorrect dependency was added to the main plugin module.  
        """.trimMargin()))
        return@forEach
      }
      val pluginPath = FList.singleton("bundled plugin ${group.includedModules[0].moduleDescriptor.moduleId.stringId}")
      group.includedModules.forEach {
        repository.collectDependencies(it.moduleDescriptor, pluginPath.prepend(it.moduleDescriptor.moduleId.stringId), allProductModules)
      }
    }

    val productResourceRoots = allProductModules.keys.flatMap { moduleId ->
      repository.getModule(moduleId).resourceRootPaths.map { it to moduleId }
    }.groupBy({ it.first }, { it.second })
    
    for (rawModuleId in descriptors.keys) {
      val moduleId = RuntimeModuleId.raw(rawModuleId)
      if (rawModuleId.startsWith(RuntimeModuleId.LIB_NAME_PREFIX)) {
        //additional libraries shouldn't cause problems because their resources should not be loaded unless they are requested from modules
        continue
      }
      val module = context.findModule(rawModuleId)
      if (module != null && (context.getModuleOutputDir(module) / "${module.name}.xml").exists()) {
        /* such descriptor indicates that it's a module in plugin model V2, and its ClassLoader ignores classes from irrelevant packages,
           so including its JAR to classpath should not cause problems */
        continue
      }
      
      
      val resourceRoots = repository.getModuleResourcePaths(moduleId)/*.filterNot {
        //ClassLoader used for classes in modules.jar ignores classes from irrelevant packages, so it's ok to have it in classpath
        it.invariantSeparatorsPathString.endsWith("/lib/modules.jar")
      }*/
      val included = resourceRoots.find { it in productResourceRoots }
      if (included != null && moduleId !in allProductModules) {
        val includedModules = productResourceRoots.getValue(included)
        val firstIncludedModuleData = includedModules.take(3).joinToString(", ") { includedModuleId ->
          "'${includedModuleId.stringId}' (<- ${allProductModules.getValue(includedModuleId).joinToString(" <- ")})"
        }
        val rest = includedModules.size - 3
        val more = if (rest > 0) " and $rest more ${StringUtil.pluralize("module", rest)}" else ""
        softly.collectAssertionError(AssertionError("""
          |Module '${moduleId.stringId}' is not part of '$productModulesModule', but it's packed in ${included.pathString},
          |which is included in classpath because $firstIncludedModuleData$more are also packed in it. 
        """.trimMargin()))
      }
    }
  }

  private fun loadProductModules(productModulesModule: String): ProductModules {
    val moduleOutputDir = context.getModuleOutputDir(context.findRequiredModule(productModulesModule))
    return RuntimeModuleRepositorySerialization.loadProductModules(moduleOutputDir.resolve("META-INF/$productModulesModule/product-modules.xml"), currentMode, repository)
  }

  private fun RuntimeModuleRepository.collectDependencies(moduleDescriptor: RuntimeModuleDescriptor, path: FList<String>, result: MutableMap<RuntimeModuleId, FList<String>> = LinkedHashMap()): MutableMap<RuntimeModuleId, FList<String>> {
    if (result.putIfAbsent(moduleDescriptor.moduleId, path) == null) {
      val newPath = path.prepend(moduleDescriptor.moduleId.stringId)
      for (dependency in moduleDescriptor.dependencies) {
        collectDependencies(dependency, newPath, result)
      }
    }
    return result
  }

  override fun close() {
    if (osSpecificDistPath != null) {
      osSpecificFilePaths.forEach {
        commonDistPath.resolve(it).moveTo(osSpecificDistPath.resolve(it))
      }
    }
  }
}