// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Checks that runtime module descriptors in the product distribution are valid.
 */
class RuntimeModuleRepositoryChecker(private val context: BuildContext) {
  private val descriptorsJarFile = context.paths.distAllDir.resolve(RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME)
  private val descriptors by lazy { RuntimeModuleRepositorySerialization.loadFromJar(descriptorsJarFile) }

  fun check(softly: SoftAssertions) {
    descriptors.values.forEach { descriptor -> 
      descriptor.dependencies.forEach { dependency ->
        softly.assertThat(dependency in descriptors)
          .describedAs("Unknown dependency '$dependency' in module '${descriptor.id}'")
          .isTrue
      }
    }
  }

  /**
   * Verifies that subset of the current product described by product-modules.xml file from [productModulesModule] can be loaded as a
   * separate product: JARs referenced from its modules must not include resources from modules not included to the product, or they are
   * split by packages in a way that the class-loader may load relevant classes only.
   */
  fun checkIntegrityOfEmbeddedProduct(productModulesModule: String, softly: SoftAssertions) {
    val moduleOutputDir = context.getModuleOutputDir(context.findRequiredModule(productModulesModule))
    val repository = RuntimeModuleRepository.create(descriptorsJarFile)
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(moduleOutputDir.resolve("META-INF/$productModulesModule/product-modules.xml"), repository)
    
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
  
  private fun RuntimeModuleRepository.collectDependencies(moduleDescriptor: RuntimeModuleDescriptor, result: MutableSet<RuntimeModuleId> = HashSet()): Set<RuntimeModuleId> {
    if (result.add(moduleDescriptor.moduleId)) {
      for (dependency in moduleDescriptor.dependencies) {
        collectDependencies(dependency, result)
      }
    }
    return result
  }
}