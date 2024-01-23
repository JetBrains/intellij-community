// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.runtime.repository.*
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.util.lang.PathClassLoader
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.nio.file.Files
import java.nio.file.Path

class ModuleBasedProductLoadingStrategy(internal val moduleRepository: RuntimeModuleRepository) : ProductLoadingStrategy() {
  private val productModules by lazy {
    val rootModuleName = System.getProperty(PLATFORM_ROOT_MODULE_PROPERTY)
    if (rootModuleName == null) {
      error("'$PLATFORM_ROOT_MODULE_PROPERTY' system property is not specified")
    }
    val currentModeId = System.getProperty(PLATFORM_PRODUCT_MODE_PROPERTY, ProductMode.LOCAL_IDE.id)
    val currentMode = ProductMode.entries.find { it.id == currentModeId}
    if (currentMode == null) {
      error("Unknown mode '$currentModeId' specified in '$PLATFORM_PRODUCT_MODE_PROPERTY' system property")
    }

    val rootModule = moduleRepository.getModule(RuntimeModuleId.module(rootModuleName))
    val productModulesPath = "META-INF/$rootModuleName/product-modules.xml"
    val moduleGroupStream = rootModule.readFile(productModulesPath)
    if (moduleGroupStream == null) {
      error("$productModulesPath is not found in '$rootModuleName' module")
    }
    RuntimeModuleRepositorySerialization.loadProductModules(moduleGroupStream, productModulesPath, currentMode, moduleRepository)
  }
  
  override fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader) {
    val mainGroupClassPath = productModules.mainModuleGroup.includedModules.flatMapTo(LinkedHashSet()) { 
      it.moduleDescriptor.resourceRootPaths 
    }
    val classPath = (bootstrapClassLoader as PathClassLoader).classPath
    logger<ModuleBasedProductLoadingStrategy>().info("New classpath roots:\n${(mainGroupClassPath - classPath.baseUrls.toSet()).joinToString("\n")}")
    classPath.addFiles(mainGroupClassPath)
  }

  override fun loadBundledPluginDescriptors(scope: CoroutineScope,
                                            bundledPluginDir: Path?,
                                            isUnitTestMode: Boolean,
                                            context: DescriptorListLoadingContext,
                                            zipFilePool: ZipFilePool?): List<Deferred<IdeaPluginDescriptorImpl?>> {
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
  
  private fun loadPluginDescriptorFromRuntimeModule(pluginModuleGroup: RuntimeModuleGroup,
                                                    context: DescriptorListLoadingContext,
                                                    zipFilePool: ZipFilePool?): IdeaPluginDescriptorImpl? {
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
    val descriptor = if (Files.isDirectory(mainResourceRoot)) {
      loadDescriptorFromDir(
        file = mainResourceRoot,
        descriptorRelativePath = PluginManagerCore.PLUGIN_XML_PATH,
        pluginPath = mainResourceRoot,
        context = context,
        isBundled = true,
        isEssential = false,
        useCoreClassLoader = false,
        pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
      )
    }
    else {
      loadDescriptorFromJar(
        file = mainResourceRoot,
        fileName = PluginManagerCore.PLUGIN_XML,
        pathResolver = ModuleBasedPluginXmlPathResolver(allResourceRoots.toList(), includedModules),
        parentContext = context,
        isBundled = true,
        isEssential = false,
        useCoreClassLoader = false,
        pluginPath = mainResourceRoot.parent.parent,
        pool = zipFilePool
      )
    }
    val requiredLibraries = collectRequiredLibraryModules(pluginModuleGroup)
    if (requiredLibraries.isNotEmpty()) {
      thisLogger().debug("Additional library modules will be added to classpath of $pluginModuleGroup: $requiredLibraries")
      requiredLibraries.flatMapTo(allResourceRoots) { it.resourceRootPaths }
    }
    descriptor?.jarFiles = allResourceRoots.toList()
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

private const val PLATFORM_ROOT_MODULE_PROPERTY = "intellij.platform.root.module"
private const val PLATFORM_PRODUCT_MODE_PROPERTY = "intellij.platform.product.mode"