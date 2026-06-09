// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization.loadProductModules
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.util.containers.FList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.SoftAssertions
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.hasModuleOutputPath
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.impl.getOsAndArchSpecificDistDirectory
import org.jetbrains.intellij.build.impl.moduleRepository.MODULE_DESCRIPTORS_COMPACT_PATH
import java.io.IOException
import kotlin.io.path.exists
import kotlin.io.path.pathString

/**
 * Checks that runtime module descriptors in the product distribution are valid.
 */
class RuntimeModuleRepositoryChecker private constructor(
  private val moduleOutputProvider: ModuleOutputProvider,
  private val bundledPluginDirectoriesToSkip: Set<String>,
  private val presentableProductName: String,
  runtimeModuleRepositoryReader: () -> RuntimeModuleRepository,
) {

  private val repository by lazy(runtimeModuleRepositoryReader)

  companion object {
    fun checkProductModules(productModulesModule: String, context: BuildContext, softly: SoftAssertions) {
      createCheckers(context).forEach {
        it.checkProductModules(productModulesModule, softly)
      }
    }

    /**
     * Verifies that the bundled plugins specified in product-modules.xml file in [productModulesModule] are present in the distribution.
     */
    fun checkBundledPluginsArePresent(productModulesModule: String, context: BuildContext, isEmbeddedVariant: Boolean, softly: SoftAssertions) {
      createCheckers(context).forEach {
        it.checkBundledPluginsArePresent(productModulesModule, softly, isEmbeddedVariant)
      }
    }

    /**
     * Verifies that the frontend part of the current product described by the product-modules.xml file from [productModulesModule] can be loaded as a
     * separate product: JARs referenced from its modules must not include resources from modules not included to the product, or they are
     * split by packages in a way that the class-loader may load relevant classes only.
     */
    fun checkIntegrityOfEmbeddedFrontend(productModulesModule: String, context: BuildContext, softly: SoftAssertions) {
      createCheckers(context).forEach {
        it.checkIntegrityOfEmbeddedFrontend(productModulesModule, softly)
      }
    }

    fun checkRuntimeModuleRepositoryForEmbeddedFrontend(
      runtimeModuleRepository: RuntimeModuleRepository,
      productModulesModule: String,
      moduleOutputProvider: ModuleOutputProvider,
      presentableProductName: String,
      softly: SoftAssertions,
    ) {
      val checker = RuntimeModuleRepositoryChecker(moduleOutputProvider, bundledPluginDirectoriesToSkip = emptySet(), presentableProductName) {
        runtimeModuleRepository
      }
      checker.checkIntegrityOfEmbeddedFrontend(productModulesModule, softly)
      checker.checkIntegrityOfEmbeddedFrontend(productModulesModule, softly)
      checker.checkBundledPluginsArePresent(productModulesModule, softly, isEmbeddedVariant = true)
    }

    private fun createCheckers(context: BuildContext): List<RuntimeModuleRepositoryChecker> {
      val moduleOutputProvider = context.outputProvider
      val bundledPluginDirectoriesToSkip = context.options.bundledPluginDirectoriesToSkip
      val presentableProductName = context.applicationInfo.shortProductName
      val commonModuleRepositoryPath = context.paths.distAllDir.resolve(MODULE_DESCRIPTORS_COMPACT_PATH)
      if (commonModuleRepositoryPath.exists()) {
        return listOf(RuntimeModuleRepositoryChecker(moduleOutputProvider, bundledPluginDirectoriesToSkip, presentableProductName) {
          RuntimeModuleRepository.create(commonModuleRepositoryPath)
        })
      }
      return SUPPORTED_DISTRIBUTIONS
        .mapNotNull { distribution ->
          val osSpecificDistPath = getOsAndArchSpecificDistDirectory(distribution.os, distribution.arch, distribution.libcImpl, context)
          val osSpecificModuleRepositoryPath = osSpecificDistPath.resolve(MODULE_DESCRIPTORS_COMPACT_PATH)
          if (osSpecificModuleRepositoryPath.exists()) {
            RuntimeModuleRepositoryChecker(moduleOutputProvider, bundledPluginDirectoriesToSkip, presentableProductName) {
              RuntimeModuleRepository.create(osSpecificModuleRepositoryPath)
            }
          }
          else null
        }
    }
  }

  private fun checkProductModules(productModulesModule: String, softly: SoftAssertions) {
    try {
      val productModules = loadProductModules(productModulesModule, this@RuntimeModuleRepositoryChecker.moduleOutputProvider)
      val corePluginForFrontendHeader = findCorePluginHeaderForFrontend(softly) ?: return
      val corePluginResourceRoots = corePluginForFrontendHeader.includedModules
            .asSequence()
            .filterNot { it.moduleId.namespace.endsWith(RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX) }
            .flatMap { included ->
              repository.findModuleHeader(included.moduleId)?.let { module -> module.ownClasspath.map { it to included.moduleId } } ?: emptyList()
            }
            .groupBy({ it.first }, { it.second })

      val pluginHeaders = loadBundledPluginHeaders(productModules, softly)
      pluginHeaders.forEach { pluginHeader ->
        for (includedModule in pluginHeader.includedModules) {
          val pluginModule = repository.findModuleHeader(includedModule.moduleId) ?: continue

          //todo: remove when PY-89477 is fixed (`intellij.pycharm.community` module contains two classes and some resources only, adding it to two classpaths shouldn't cause problems)
          if (pluginModule.moduleId.name == "intellij.pycharm.community") continue

          for (resourcePath in pluginModule.ownClasspath) {
            val corePluginModules = corePluginResourceRoots[resourcePath]
            if (corePluginModules != null) {
              val corePluginModuleListString =
                when (corePluginModules.size) {
                  1 -> "module ${corePluginModules.first().displayName}"
                  2,3 -> "modules ${corePluginModules.joinToString { it.displayName }}"
                  else -> "${corePluginModules.first().displayName} and ${corePluginModules.size - 1} more modules"
                }
              val moduleId = pluginModule.moduleId.displayName
              val pluginModuleId = pluginHeader.pluginDescriptorModuleId.displayName
              softly.registerFailure(place = moduleId, errorMessage = """
                |Module '$moduleId' from plugin '$pluginModuleId' has resource root $resourcePath,
                |which is also added as a resource root of $corePluginModuleListString from the core (platform) plugin.
                |This may lead to classes from the core plugin to be loaded by two classloaders leading to ClassCastException at runtime.
                |If '$moduleId' belongs to '$pluginModuleId' plugin, make sure that it's included in the plugin layout (if it's registered as a content module, it should be enough to remove
                |explicit references to it from the build scripts, and it'll be packed in the plugin automatically).
                |If '$moduleId' is a part of the core plugin, don't register it as a content module in '$pluginModuleId'. 
                |""".trimMargin())
            }
          }
        }
      }
    }
    catch (e: MalformedRepositoryException) { 
      softly.registerFailure(place = productModulesModule, errorMessage = "Failed to load product-modules.xml for $repository: $e", cause = e)
    }
  }

  private fun findCorePluginHeaderForFrontend(softly: SoftAssertions): RuntimePluginHeader? {
    val corePluginModuleName = "intellij.frontend.split.customization"
    val corePluginForFrontendHeader = repository.findBundledPluginHeader(RuntimeModuleId.legacyJpsModule(corePluginModuleName))
    if (corePluginForFrontendHeader == null) {
      softly.registerFailure(place = corePluginModuleName, errorMessage = "The header for the core plugin is not found by its module name '$corePluginModuleName'")
    }
    return corePluginForFrontendHeader
  }


  private fun checkIntegrityOfEmbeddedFrontend(productModulesModule: String, softly: SoftAssertions) {
    val productModules = loadProductModules(productModulesModule, this@RuntimeModuleRepositoryChecker.moduleOutputProvider)

    val allProductModules = LinkedHashMap<RuntimeModuleId, FList<String>>()
    allProductModules[RuntimeModuleId.legacyJpsModule("intellij.platform.bootstrap")] = FList.singleton("bootstrap")
    val corePluginHeader = findCorePluginHeaderForFrontend(softly) ?: return
    val corePluginHeaderPath = FList.singleton("core plugin header ${corePluginHeader.pluginDescriptorModuleId.displayName}")
    corePluginHeader.includedModules.forEach {
      allProductModules[it.moduleId] = corePluginHeaderPath
    }
    val pluginHeaders = loadBundledPluginHeaders(productModules, softly)
    pluginHeaders.forEach { header ->
      header.includedModules.forEach { includedModule ->
        if (includedModule.loadingRule == RuntimeModuleLoadingRule.EMBEDDED) {
          if (repository.findModuleHeader(includedModule.moduleId) == null) {
            softly.registerFailure(
              place = includedModule.moduleId.displayName,
              errorMessage = "Module '${includedModule.moduleId.displayName}' included as as embedded in the plugin '${header.pluginId}' is not found in the runtime module repository"
            )
            return@forEach
          }
          val pluginPath = FList.singleton("bundled plugin header ${header.pluginDescriptorModuleId.displayName}")
          allProductModules[includedModule.moduleId] = pluginPath
        }
      }
    }

    val productResourceRoots = allProductModules.keys.flatMap { moduleId ->
      val moduleHeader = repository.findModuleHeader(moduleId)
      if (moduleHeader == null) {
        softly.registerFailure(
          place = moduleId.displayName,
          errorMessage = "Module '${moduleId.displayName}' is not found in the runtime module repository"
        )
        return@flatMap emptyList<Pair<String, RuntimeModuleId>>()
      }
      moduleHeader.ownClasspath.map { it to moduleId }
    }.groupBy({ it.first }, { it.second })

    val allModuleIds = repository.bundledPluginHeaders
      .asSequence()
      .flatMap { pluginHeader ->
        pluginHeader.includedModules.asSequence().map { it.moduleId }
      }
      .toSet()
    for (moduleId in allModuleIds) {
      if (moduleId.namespace.endsWith(RuntimeModuleId.LEGACY_JPS_LIBRARY_NAMESPACE_SUFFIX)) {
        //additional libraries shouldn't cause problems because their resources should not be loaded unless they are requested from modules
        continue
      }
      val module = this@RuntimeModuleRepositoryChecker.moduleOutputProvider.findModule(moduleId.name)
      if (module != null && hasModuleOutputPath(module = module, relativePath = "${module.name}.xml", outputProvider = this@RuntimeModuleRepositoryChecker.moduleOutputProvider)) {
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
          "'${it.displayName}' (<- ${allProductModules.getValue(it).joinToString(" <- ")})"
        }
        val rest = includedModules.size - displayedModulesCount
        val embeddedProductPresentableName = "$presentableProductName Frontend"
        val more = if (rest > 0) " and $rest more ${StringUtil.pluralize("module", rest)}" else ""
        softly.registerFailure(
          place = moduleId.displayName,
          errorMessage = """
            |Module '${moduleId.displayName}' is not part of $embeddedProductPresentableName included in the full $presentableProductName distribution, but it's packed in ${included.pathString},
            |which is included in the classpath of $embeddedProductPresentableName because:
            |$firstIncludedModuleData$more are also packed in it.
            |This means that '${moduleId.displayName}' will be included in the classpath of $embeddedProductPresentableName as well. 
            |Unnecessary code and resources in the classpath may cause performance problems, also, they may cause $embeddedProductPresentableName to behave differently in a standalone 
            |installation and when invoked from $presentableProductName. To fix the problem, you should do one of the following:
            |* if other modules packed in '${included.pathString}' shouldn't be part of $embeddedProductPresentableName, remove incorrect dependencies shown above; this may require extracting additional modules;
            |* if '${moduleId.displayName}' actually should be included in $embeddedProductPresentableName, make sure that it's included either by adding it as a content module in plugin.xml, or by adding it in the main module group in product-modules.xml;
            |* if '${moduleId.displayName}' should not be included in $embeddedProductPresentableName, but other parts of ${included.pathString} should, ensure that they are put to
            |  separate JAR files; it may be enough to add a runtime dependency on 'intellij.platform.backend' to all modules which shouldn't be included to the frontend part,
            |  the build scripts will take this into account to assign separate JARs automatically; however, if custom layout is specified for a plugin, you may need to put modules
            |  to separate JARs using explicit 'withModule(...)' calls in the layout configuration.
          """.trimMargin()
        )
      }
    }
  }

  private fun loadBundledPluginHeaders(productModules: ProductModules, softly: SoftAssertions): List<RuntimePluginHeader> {
    return productModules.bundledPluginDescriptorModules.mapNotNull { pluginDescriptorModule ->
      val header = repository.findBundledPluginHeader(pluginDescriptorModule)
      if (header == null && !isBundledPluginSkipped(pluginDescriptorModule)) {
        softly.registerFailure(
          place = pluginDescriptorModule.displayName,
          errorMessage = "Plugin header for module '${pluginDescriptorModule.displayName}' is not found in the runtime module repository"
        )
      }
      header
    }
  }

  private fun checkBundledPluginsArePresent(productModulesModule: String, softly: SoftAssertions, isEmbeddedVariant: Boolean) {
    val rawProductModules = loadRawProductModulesFromOutput(productModulesModule, this@RuntimeModuleRepositoryChecker.moduleOutputProvider)
    val productName = presentableProductName
    val currentDistributionName = if (isEmbeddedVariant) productName else "'$productName Frontend'"
    for (mainModuleId in rawProductModules.bundledPluginMainModules) {
      if (isBundledPluginSkipped(mainModuleId)) continue
      val mainModule = repository.resolveModule(mainModuleId)
      if (mainModule.resolvedModule == null) {
        val problematicModule = if (mainModule.failedDependencyPath.size == 1) "it" else "its dependency ${mainModule.failedDependencyPath.reversed().joinToString(" <- ") { it.displayName }}"
        softly.registerFailure(
          place = mainModuleId.displayName,
          errorMessage = buildString {
              append("Module '${mainModuleId.displayName}' is specified as the main module of a bundled plugin in product-modules.xml in '$productModulesModule',\n")
              append("but $problematicModule cannot be found in the runtime module repository in the distribution of $currentDistributionName.\n")
              if (isEmbeddedVariant) {
                append("It means that the corresponding plugin won't be loaded when '$productName Frontend' is started from the full\n")
                append("installation of $productName\n")
              }
              append("If '${mainModuleId.displayName}' shouldn't be available in the frontend variant of $productName, remove it from product-modules.xml file\n")
              append("(or use 'without-module' tag if it comes via 'include' tag).\n")
              if (isEmbeddedVariant) {
                append("If it should, add all necessary modules to the plugin layout of the main variant of '${mainModuleId.displayName}' plugin.\n")
                append("Modules used by the frontend variant only should be put in JAR files in 'frontend-split' subdirectory so they won't be loaded in the regular IDE.\n")
              }
              else {
                append("If it should, make sure that all necessary modules are included in the distribution of $currentDistributionName.\n")
              }
              if (mainModule.failedDependencyPath.size > 1) {
                append("If some dependencies in the chain ${mainModule.failedDependencyPath.reversed().joinToString(" <- ") { it.displayName }}\n")
                append("are not actually needed, they can be removed from configuration of the corresponding JPS modules (*.iml) to fix this problem.\n")
              }
              append("Please refer to https://youtrack.jetbrains.com/articles/IJPL-A-268 to learn more how the frontend process starts.")
            }
        )
      }
    }
  }

  private fun isBundledPluginSkipped(mainModuleId: RuntimeModuleId): Boolean {
    //this doesn't support custom plugin directory names, but it's enough for tests
    val pluginDirectoryName = mainModuleId.name.removePrefix("intellij.").replace('.', '-')
    return pluginDirectoryName in bundledPluginDirectoriesToSkip
  }

  private fun SoftAssertions.registerFailure(place: String, errorMessage: String, cause: Throwable? = null) {
    val e = RuntimeModuleRepositoryCheckingFailure(place, errorMessage, cause)
    if (errorsCollected().none {
        val message = it.message
        message != null && message.lineSequence().filterNot { line -> line.startsWith("at ") }.joinToString("\n").trim() == e.message?.trim() 
    }) {
      collectAssertionError(e)
    }
  }
}

class RuntimeModuleRepositoryCheckingFailure(val place: String, val errorMessage: String, cause: Throwable? = null): AssertionError("$place: $errorMessage", cause)

private fun loadProductModules(productModulesModule: String, outputProvider: ModuleOutputProvider): ProductModules {
  val relativePath = "META-INF/$productModulesModule/product-modules.xml"
  val debugName = "($relativePath file in $productModulesModule)"

  @Suppress("RAW_RUN_BLOCKING")
  val content = runBlocking(Dispatchers.IO) {
    outputProvider.readFileContentFromModuleOutput(outputProvider.findRequiredModule(productModulesModule), relativePath)
  } ?: throw MalformedRepositoryException("File '$relativePath' is not found in module $productModulesModule output")
  try {
    return loadProductModules(
      content.inputStream(),
      debugName,
      createModuleOutputResourceFileResolver(outputProvider)
    )
  }
  catch (e: IOException) {
    throw MalformedRepositoryException("Failed to load module group from $debugName", e)
  }
}
