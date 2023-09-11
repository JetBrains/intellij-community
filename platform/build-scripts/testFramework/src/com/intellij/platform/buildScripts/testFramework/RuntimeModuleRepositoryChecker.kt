// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.platform.runtime.repository.*
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
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
    fun checkProductModules(productModulesModule: String, context: BuildContext, softly: SoftAssertions) {
      createCheckers(context).forEach { 
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
    fun checkIntegrityOfEmbeddedProduct(productModulesModule: String, context: BuildContext, softly: SoftAssertions) {
      createCheckers(context).forEach {
        it().use { checker ->
          checker.checkIntegrityOfEmbeddedProduct(productModulesModule, softly)
        }
      }
    }

    private fun createCheckers(context: BuildContext): List<() -> RuntimeModuleRepositoryChecker> {
      val commonDistPath = context.paths.distAllDir 
      if (commonDistPath.resolve(MODULE_DESCRIPTORS_JAR_PATH).exists()) {
        return listOf { RuntimeModuleRepositoryChecker(commonDistPath, null, context) }
      }
      return SUPPORTED_DISTRIBUTIONS
        .mapNotNull { distribution ->
          val osSpecificDistPath = getOsAndArchSpecificDistDirectory(distribution.os, distribution.arch, context)
          if (osSpecificDistPath.resolve(MODULE_DESCRIPTORS_JAR_PATH).exists()) {
            { RuntimeModuleRepositoryChecker(commonDistPath, osSpecificDistPath, context) }
          }
          else null
        }
    }
  }
  private val descriptors by lazy { RuntimeModuleRepositorySerialization.loadFromJar(descriptorsJarFile) }

  private fun checkProductModules(productModulesModule: String, softly: SoftAssertions) {
    try {
      val productModules = loadProductModules(productModulesModule)
      val allDependencies = HashSet<RuntimeModuleId>()
      productModules.mainModuleGroup.includedModules.forEach { 
        repository.collectDependencies(it.moduleDescriptor, allDependencies)
      }
      productModules.bundledPluginModuleGroups.forEach { group ->
        group.includedModules.forEach { 
          repository.collectDependencies(it.moduleDescriptor, allDependencies)
        }
      }
    }
    catch (e: MalformedRepositoryException) { 
      softly.collectAssertionError(AssertionError("Failed to load product-modules.xml for $descriptorsJarFile: $e", e))
    }
  }

  private fun checkIntegrityOfEmbeddedProduct(productModulesModule: String, softly: SoftAssertions) {
    val productModules = loadProductModules(productModulesModule)

    val allProductModules = LinkedHashSet<RuntimeModuleId>()
    allProductModules.add(RuntimeModuleId.module("intellij.platform.bootstrap"))
    productModules.mainModuleGroup.includedModules.flatMapTo(allProductModules) {
      repository.collectDependencies(it.moduleDescriptor)
    }
    productModules.bundledPluginModuleGroups.flatMapTo(allProductModules) { group ->
      group.includedModules.flatMap {
        repository.collectDependencies(it.moduleDescriptor)
      }
    }

    val productResourceRoots = allProductModules.flatMap { moduleId ->
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
        softly.collectAssertionError(AssertionError("""
          |Module '${moduleId.stringId}' is not part of '$productModulesModule', but it's packed in ${included.pathString},
          |which is included in classpath because ${productResourceRoots.getValue(included).joinToString(", ") { 
            it.stringId  
          }} are also packed in it. 
          |
        """.trimMargin()))
      }
    }
  }

  private fun loadProductModules(productModulesModule: String): ProductModules {
    val moduleOutputDir = context.getModuleOutputDir(context.findRequiredModule(productModulesModule))
    return RuntimeModuleRepositorySerialization.loadProductModules(moduleOutputDir.resolve("META-INF/$productModulesModule/product-modules.xml"), repository)
  }

  private fun RuntimeModuleRepository.collectDependencies(moduleDescriptor: RuntimeModuleDescriptor, result: MutableSet<RuntimeModuleId> = HashSet()): Set<RuntimeModuleId> {
    if (result.add(moduleDescriptor.moduleId)) {
      for (dependency in moduleDescriptor.dependencies) {
        collectDependencies(dependency, result)
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