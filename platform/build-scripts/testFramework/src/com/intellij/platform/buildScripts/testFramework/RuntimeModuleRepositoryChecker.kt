// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.impl.ServiceModuleMapping
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.containers.FList
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.*
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Checks that runtime module descriptors in the product distribution are valid.
 */
@OptIn(ExperimentalPathApi::class)
internal class RuntimeModuleRepositoryChecker private constructor(
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
    suspend fun checkProductModules(productModulesModule: String, context: BuildContext, softly: SoftAssertions) {
      createCheckers(context).forEach {
        it().use { checker ->
          checker.checkProductModules(productModulesModule, softly)
        }
      }
    }

    /**
     * Verifies that the frontend part of the current product described by the product-modules.xml file from [productModulesModule] can be loaded as a
     * separate product: JARs referenced from its modules must not include resources from modules not included to the product, or they are
     * split by packages in a way that the class-loader may load relevant classes only.
     */
    suspend fun checkIntegrityOfEmbeddedFrontend(productModulesModule: String, context: BuildContext, softly: SoftAssertions) {
      createCheckers(context).forEach {
        it().use { checker ->
          checker.checkIntegrityOfEmbeddedFrontend(productModulesModule, softly)
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
  private val moduleRepositoryData by lazy { RuntimeModuleRepositorySerialization.loadFromJar(descriptorsJarFile) }

  private suspend fun checkProductModules(productModulesModule: String, softly: SoftAssertions) {
    try {
      val productModules = loadProductModules(productModulesModule)
      val serviceModuleMapping = ServiceModuleMapping.buildMapping(productModules, includeDebugInfoInErrorMessage = true)
      val mainGroupModuleResourceRoots = 
        productModules.mainModuleGroup.includedModules
          .asSequence()
          .map { it.moduleDescriptor }
          .filter { !it.moduleId.stringId.startsWith(RuntimeModuleId.LIB_NAME_PREFIX) }
          .flatMap { moduleDescriptor -> moduleDescriptor.resourceRootPaths.map { it to moduleDescriptor.moduleId } }
          .groupBy({ it.first }, { it.second })
      
      productModules.bundledPluginModuleGroups.forEach { group ->
        val allPluginModules = group.includedModules.map { it.moduleDescriptor } + serviceModuleMapping.getAdditionalModules(group)
        if (group.mainModule.moduleId == RuntimeModuleId.module("intellij.performanceTesting.async") && context.applicationInfo.productCode == "IC") {
          //'intellij.performanceTesting.async' bundled with IDEA Community includes modules which are included in the core plugin for IDEA Ultimate, 
          //so it won't be loaded in IDEA Community, see IJPL-186414 
          return@forEach
        }
        
        for (pluginModule in allPluginModules) {
          if (pluginModule.moduleId == RuntimeModuleId.projectLibrary("commons-lang3")) {
            //ignore this error until IJPL-671 is fixed
            continue
          }
          
          for (resourcePath in pluginModule.resourceRootPaths) {
            val mainModules = mainGroupModuleResourceRoots[resourcePath]
            if (mainModules != null) {
              val mainModuleListString = 
                if (mainModules.size < 3) mainModules.joinToString { it.stringId } 
                else "${mainModules.first().stringId} and ${mainModules.size - 1} more modules"
              val moduleId = pluginModule.moduleId.stringId
              val pluginModuleId = group.mainModule.moduleId.stringId
              softly.collectAssertionErrorIfNotRegisteredYet(
                AssertionError("""
                |Module '$moduleId' from plugin '$pluginModuleId' has resource root ${commonDistPath.relativize(resourcePath)},
                |which is also added as a resource root of modules from the core (platform) plugin ($mainModuleListString).
                |This may lead to classes from the core plugin to be loaded by two classloaders leading to ClassCastException at runtime.
                |If '$moduleId' belongs to '$pluginModuleId' plugin, make sure that it's included in the plugin layout (if it's registered as a content module, it should be enough to remove
                |explicit references to it from the build scripts, and it'll be packed in the plugin automatically).
                |If '$moduleId' is a part of the core plugin, don't register it as a content module in '$pluginModuleId', and register it in `main-root-modules` tag in
                |`product-modules.xml` instead. 
                |""".trimMargin()))
            }
          }
        }
      }
    }
    catch (e: MalformedRepositoryException) { 
      softly.collectAssertionErrorIfNotRegisteredYet(AssertionError("Failed to load product-modules.xml for $descriptorsJarFile: $e", e))
    }
  }

  private suspend fun checkIntegrityOfEmbeddedFrontend(productModulesModule: String, softly: SoftAssertions) {
    val productModules = loadProductModules(productModulesModule)

    val allProductModules = LinkedHashMap<RuntimeModuleId, FList<String>>()
    allProductModules[RuntimeModuleId.module("intellij.platform.bootstrap")] = FList.singleton("bootstrap")
    val mainModuleGroupPath = FList.singleton("main module group")
    productModules.mainModuleGroup.includedModules.forEach { mainModule ->
      repository.collectDependencies(mainModule.moduleDescriptor, mainModuleGroupPath, allProductModules)
    }
    productModules.bundledPluginModuleGroups.forEach { group ->
      if (group.includedModules.isEmpty()) {
        softly.collectAssertionErrorIfNotRegisteredYet(AssertionError("""
           |No modules from '$group' are included in a product running in the frontend mode, so corresponding plugin won't be loaded.
           |Probably it indicates that some incorrect dependency was added to the main plugin module.  
        """.trimMargin()))
        return@forEach
      }
      val pluginPath = FList.singleton("bundled plugin ${group.mainModule.moduleId.stringId}")
      group.includedModules.forEach {
        repository.collectDependencies(it.moduleDescriptor, pluginPath.prepend(it.moduleDescriptor.moduleId.stringId), allProductModules)
      }
    }

    val productResourceRoots = allProductModules.keys.flatMap { moduleId ->
      repository.getModule(moduleId).resourceRootPaths.map { it to moduleId }
    }.groupBy({ it.first }, { it.second })
    
    for (rawModuleId in moduleRepositoryData.allIds) {
      val moduleId = RuntimeModuleId.raw(rawModuleId)
      if (rawModuleId.startsWith(RuntimeModuleId.LIB_NAME_PREFIX)) {
        //additional libraries shouldn't cause problems because their resources should not be loaded unless they are requested from modules
        continue
      }
      val module = context.findModule(rawModuleId)
      if (module != null && context.hasModuleOutputPath(module, "${module.name}.xml")) {
        // such a descriptor indicates that it's a module in plugin model V2, and its ClassLoader ignores classes from irrelevant packages,
        // so including its JAR to classpath should not cause problems
        continue
      }
      
      val resourceRoots = repository.getModuleResourcePaths(moduleId)/*.filterNot {
        //ClassLoader used for classes in modules.jar ignores classes from irrelevant packages, so it's ok to have it in classpath
        it.invariantSeparatorsPathString.endsWith("/lib/modules.jar")
      }*/
      val included = resourceRoots.find { it in productResourceRoots }
      if (included != null && moduleId !in allProductModules) {
        val includedModules = productResourceRoots.getValue(included)
        val displayedModulesCount = 10
        val firstIncludedModuleData = includedModules.take(displayedModulesCount).joinToString(separator = System.lineSeparator()) {
          "'${it.stringId}' (<- ${allProductModules.getValue(it).joinToString(" <- ")})"
        }
        val rest = includedModules.size - displayedModulesCount
        val embeddedProductPresentableName = "${context.applicationInfo.shortProductName} Frontend"
        val more = if (rest > 0) " and $rest more ${StringUtil.pluralize("module", rest)}" else ""
        softly.collectAssertionErrorIfNotRegisteredYet(AssertionError("""
          |Module '${moduleId.stringId}' is not part of $embeddedProductPresentableName included in the full ${context.applicationInfo.shortProductName} distribution, but it's packed in ${included.pathString},
          |which is included in the classpath of $embeddedProductPresentableName because:
          |$firstIncludedModuleData$more are also packed in it.
          |This means that '${moduleId.stringId}' will be included in the classpath of $embeddedProductPresentableName as well. 
          |Unnecessary code and resources in the classpath may cause performance problems, also, they may cause $embeddedProductPresentableName to behave differently in a standalone 
          |installation and when invoked from ${context.applicationInfo.fullProductName}. To fix the problem, you should do one of the following:
          |* if other modules packed in '${included.pathString}' shouldn't be part of $embeddedProductPresentableName, remove incorrect dependencies shown above; this may require extracting additional modules;
          |* if '${moduleId.stringId}' actually should be included in $embeddedProductPresentableName, make sure that it's included either by adding it as a content module in plugin.xml, or by adding it in the main module group in product-modules.xml;
          |* if '${moduleId.stringId}' should not be included in $embeddedProductPresentableName, but other parts of ${included.pathString} should, ensure that they are put to
          |  separate JAR files; it may be enough to add a runtime dependency on 'intellij.platform.backend' to all modules which shouldn't be included to the frontend part,
          |  the build scripts will take this into account to assign separate JARs automatically; however, if custom layout is specified for a plugin, you may need to put modules
          |  to separate JARs using explicit 'withModule(...)' calls in the layout configuration.
        """.trimMargin()))
      }
    }
  }
  
  private fun SoftAssertions.collectAssertionErrorIfNotRegisteredYet(e: AssertionError) {
    if (errorsCollected().none {
        val message = it.message
        message != null && message.lineSequence().filterNot { line -> line.startsWith("at ") }.joinToString("\n").trim() == e.message?.trim() 
    }) {
      collectAssertionError(e)
    }
  }

  private suspend fun loadProductModules(productModulesModule: String): ProductModules {
    val relativePath = "META-INF/$productModulesModule/product-modules.xml"
    val debugName = "${context.getModuleOutputDir(context.findRequiredModule(productModulesModule))}/$relativePath"
    val content = context.getModuleOutputFileContent(context.findRequiredModule(productModulesModule), relativePath)
                  ?: throw MalformedRepositoryException("File '$relativePath' is not found in module $productModulesModule output")
    try {
      return ProductModulesSerialization.loadProductModules(content.inputStream(), debugName, ProductMode.FRONTEND, repository)
    }
    catch (e: IOException) {
      throw MalformedRepositoryException("Failed to load module group from $debugName", e)
    }
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