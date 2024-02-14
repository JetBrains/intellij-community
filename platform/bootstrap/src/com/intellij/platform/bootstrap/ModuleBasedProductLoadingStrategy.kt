// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.runtime.product.IncludedRuntimeModule
import com.intellij.platform.runtime.product.ModuleImportance
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.RuntimeModuleGroup
import com.intellij.platform.runtime.product.impl.IncludedRuntimeModuleImpl
import com.intellij.platform.runtime.product.serialization.ProductModulesSerialization
import com.intellij.platform.runtime.repository.MalformedRepositoryException
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension

internal class ModuleBasedProductLoadingStrategy(internal val moduleRepository: RuntimeModuleRepository) : ProductLoadingStrategy() {
  @OptIn(ExperimentalStdlibApi::class)
  private val currentMode by lazy {
    val currentModeId = System.getProperty(PLATFORM_PRODUCT_MODE_PROPERTY, ProductMode.LOCAL_IDE.id)
    val currentMode = ProductMode.entries.find { it.id == currentModeId }
    if (currentMode == null) {
      error("Unknown mode '$currentModeId' specified in '$PLATFORM_PRODUCT_MODE_PROPERTY' system property")
    }
    currentMode
  }
  
  private val productModules by lazy {
    val rootModuleName = System.getProperty(PLATFORM_ROOT_MODULE_PROPERTY)
    if (rootModuleName == null) {
      error("'$PLATFORM_ROOT_MODULE_PROPERTY' system property is not specified")
    }

    val rootModule = moduleRepository.getModule(RuntimeModuleId.module(rootModuleName))
    val productModulesPath = "META-INF/$rootModuleName/product-modules.xml"
    val moduleGroupStream = rootModule.readFile(productModulesPath)
    if (moduleGroupStream == null) {
      error("$productModulesPath is not found in '$rootModuleName' module")
    }
    ProductModulesSerialization.loadProductModules(moduleGroupStream, productModulesPath, currentMode, moduleRepository)
  }
  
  override val currentModeId: String
    get() = currentMode.id

  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
    val mainGroupClassPath = productModules.mainModuleGroup.includedModules.flatMapTo(LinkedHashSet()) {
      it.moduleDescriptor.resourceRootPaths
    }
    val classPath = (bootstrapClassLoader as PathClassLoader).classPath
    logger<ModuleBasedProductLoadingStrategy>().info("New classpath roots:\n${(mainGroupClassPath - classPath.baseUrls.toSet()).joinToString("\n")}")
    classPath.addFiles(mainGroupClassPath)
  }

  override fun loadBundledPluginDescriptors(
    scope: CoroutineScope,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): List<Deferred<IdeaPluginDescriptorImpl?>> {
    val mainGroupModules = productModules.mainModuleGroup.includedModules.map { it.moduleDescriptor.moduleId }
    return productModules.bundledPluginModuleGroups.map { moduleGroup ->
      scope.async {
        if (moduleGroup.includedModules.none { it.moduleDescriptor.moduleId in mainGroupModules }) {
          loadPluginDescriptorFromRuntimeModule(moduleGroup, context, zipFilePool)
        }
        else {
          /* todo: intellij.performanceTesting.async plugin has different distributions for different IDEs, in some IDEs it has dependencies 
             on 'intellij.profiler.common' and other module from the platform, in other IDEs it includes them as its own content. In the
             latter case we currently cannot run it using the modular loader, because these modules will be loaded twice. */
          logger<ModuleBasedProductLoadingStrategy>().debug("Skipped $moduleGroup: ${moduleGroup.includedModules}")
          null
        }
      }
    }
  }

  override fun loadCustomPluginDescriptors(scope: CoroutineScope, customPluginDir: Path, context: DescriptorListLoadingContext, 
                                           zipFilePool: ZipFilePool): Collection<Deferred<IdeaPluginDescriptorImpl?>> {
    if (!Files.isDirectory(customPluginDir)) {
      return emptyList()
    }

    return Files.newDirectoryStream(customPluginDir).use { dirStream ->
      val deferredDescriptors = ArrayList<Deferred<IdeaPluginDescriptorImpl?>>()
      val additionalRepositoryPaths = ArrayList<Path>()
      dirStream.forEach { file ->
        val moduleRepository = file.resolve("module-descriptors.jar")
        if (moduleRepository.exists()) {
          additionalRepositoryPaths.add(moduleRepository)
        }
        else {
          deferredDescriptors.add(scope.async {
            loadDescriptorFromFileOrDir(
              file = file,
              context = context,
              pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
              pool = zipFilePool,
            )
          })
        }
      }
      deferredDescriptors.addAll(loadPluginDescriptorsFromAdditionalRepositories(scope, additionalRepositoryPaths, context, zipFilePool))
      deferredDescriptors
    }
  }

  private fun loadPluginDescriptorsFromAdditionalRepositories(scope: CoroutineScope,
                                                              repositoryPaths: List<Path>,
                                                              context: DescriptorListLoadingContext,
                                                              zipFilePool: ZipFilePool): Collection<Deferred<IdeaPluginDescriptorImpl?>> {
    val repositoriesByPaths = scope.async {
      val repositoriesByPaths = repositoryPaths.associateWith {
        try {
          RuntimeModuleRepositorySerialization.loadFromJar(it)
        }
        catch (e: MalformedRepositoryException) {
          logger<ModuleBasedProductLoadingStrategy>().warn("Failed to load module repository for a custom plugin: $e", e)
          null
        } 
      }
      (moduleRepository as RuntimeModuleRepositoryImpl).loadAdditionalRepositories(repositoriesByPaths.values.filterNotNull())
      repositoriesByPaths
    }
    return repositoryPaths.map { path -> 
      scope.async { 
        val repositoryDataMap = repositoriesByPaths.await()
        val repositoryData = repositoryDataMap[path] ?: return@async null
        val mainModuleId = repositoryData.mainPluginModuleId ?: return@async null
        try {
          val mainModule = moduleRepository.getModule(RuntimeModuleId.raw(mainModuleId))
          /* 
            It would be probably better to reuse PluginModuleGroup here, and load information about additional modules from plugin.xml. 
            However, currently this won't work because plugin model v2 requires that there is an xml configuration file for each module
            mentioned in the <content> tag, but in the test plugins we have modules without configuration files. 
          */
          val descriptors = ArrayList<RuntimeModuleDescriptor>()
          descriptors.add(mainModule)
          repositoryData.allIds.asSequence()
            .filter { it != mainModuleId }
            .mapTo(descriptors) { moduleRepository.getModule(RuntimeModuleId.raw(it)) }
          val moduleGroup = CustomPluginModuleGroup(descriptors)
          loadPluginDescriptorFromRuntimeModule(moduleGroup, context, zipFilePool)
        }
        catch (t: Throwable) {
          logger<ModuleBasedProductLoadingStrategy>().warn("Failed to load custom plugin '$mainModuleId': $t", t)
          null
        }
      }
    }
  }

  private fun loadPluginDescriptorFromRuntimeModule(
    pluginModuleGroup: RuntimeModuleGroup,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): IdeaPluginDescriptorImpl? {
    val includedModules = pluginModuleGroup.includedModules
    //we rely on the fact that PluginXmlReader.loadPluginModules adds the main module to the beginning of the list  
    val mainModule = includedModules.firstOrNull()
    if (mainModule == null) {
      thisLogger().warn("No modules are included in $pluginModuleGroup, the plugin won't be loaded")
      return null
    }

    val mainResourceRoot = mainModule.moduleDescriptor.resourceRootPaths.singleOrNull()
    if (mainResourceRoot == null) {
      thisLogger().warn("Main plugin module must have single resource root, so '${mainModule.moduleDescriptor.moduleId.stringId}' with roots ${mainModule.moduleDescriptor.resourceRootPaths} won't be loaded")
      return null
    }

    val allResourceRoots = includedModules.flatMapTo(LinkedHashSet()) { it.moduleDescriptor.resourceRootPaths }
    val requiredLibraries = collectRequiredLibraryModules(pluginModuleGroup)
    if (requiredLibraries.isNotEmpty()) {
      thisLogger().debug("Additional library modules will be added to classpath of $pluginModuleGroup: $requiredLibraries")
      requiredLibraries.flatMapTo(allResourceRoots) { it.resourceRootPaths }
    }
    val allResourceRootsList = allResourceRoots.toList();
    
    val descriptor = if (Files.isDirectory(mainResourceRoot)) {
      loadDescriptorFromDir(
        dir = mainResourceRoot,
        pluginDir = mainResourceRoot,
        context = context,
        isBundled = true,
        pathResolver = ModuleBasedPluginXmlPathResolver(
          includedModules = includedModules,
          fallbackResolver = PluginXmlPathResolver(allResourceRootsList.filter { it.extension == "jar" }, zipFilePool),
        )
      )
    }
    else {
      val defaultResolver = PluginXmlPathResolver(allResourceRootsList, zipFilePool)
      val pathResolver = 
        if (allResourceRootsList.size == 1) {
          defaultResolver
        }
        else {
          ModuleBasedPluginXmlPathResolver(includedModules = includedModules, fallbackResolver = defaultResolver)
        }
      loadDescriptorFromJar(
        file = mainResourceRoot,
        pathResolver = pathResolver,
        parentContext = context,
        isBundled = true,
        pluginDir = mainResourceRoot.parent.parent,
        pool = zipFilePool,
      )
    }
    descriptor?.jarFiles = allResourceRootsList
    return descriptor
  }

  private fun collectRequiredLibraryModules(pluginModuleGroup: RuntimeModuleGroup): Set<RuntimeModuleDescriptor> {
    /* Since libraries used by a plugin aren't mentioned in <content> tag in plugin.xml, we need to add them to the classpath automatically.
       This function returns all library modules from dependencies of plugin modules which aren't present in the main module group;
       Todo: support cases when a library is already included in a plugin on which the current plugin depends.
     */
    val includedInPlatform = productModules.mainModuleGroup.includedModules.mapTo(HashSet()) { it.moduleDescriptor }

    return pluginModuleGroup.includedModules.flatMapTo(LinkedHashSet()) { includedModule ->
      includedModule.moduleDescriptor.dependencies.filter { dependency ->
        dependency.moduleId.stringId.startsWith(RuntimeModuleId.LIB_NAME_PREFIX) && dependency !in includedInPlatform
      }
    }
  }

  override val shouldLoadDescriptorsFromCoreClassPath: Boolean
    get() = false

  override fun isOptionalProductModule(moduleName: String): Boolean {
    return productModules.mainModuleGroup.optionalModuleIds.contains(RuntimeModuleId.raw(moduleName))
  }
}

private class CustomPluginModuleGroup(moduleDescriptors: List<RuntimeModuleDescriptor>) : RuntimeModuleGroup {
  private val includedModules = moduleDescriptors.map { IncludedRuntimeModuleImpl(it, ModuleImportance.FUNCTIONAL) } 
  override fun getIncludedModules(): List<IncludedRuntimeModule> = includedModules 
  override fun getOptionalModuleIds(): Set<RuntimeModuleId> = emptySet()
}

private const val PLATFORM_ROOT_MODULE_PROPERTY = "intellij.platform.root.module"
private const val PLATFORM_PRODUCT_MODE_PROPERTY = "intellij.platform.product.mode"