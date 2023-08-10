// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.PathManager
import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This class is temporarily added to support two ways of loading the plugin descriptors: [the old one][PathBasedProductLoadingStrategy] 
 * which is based on layout of JAR files in the IDE installation directory and [the new one][com.intellij.platform.bootstrap.ModuleBasedProductLoadingStrategy]
 * which uses information from runtime module descriptors.
 */
@ApiStatus.Internal
abstract class ProductLoadingStrategy {
  companion object {
    @Volatile
    @JvmStatic
    private var ourStrategy: ProductLoadingStrategy? = null
    
    @JvmStatic
    var strategy: ProductLoadingStrategy
      get() {
        if (ourStrategy == null) {
          ourStrategy = PathBasedProductLoadingStrategy()
        }
        return ourStrategy!!
      }
      set(value) {
        ourStrategy = value
      }
  }

  abstract fun loadBundledPluginDescriptors(scope: CoroutineScope,
                                            bundledPluginDir: Path?,
                                            isUnitTestMode: Boolean,
                                            context: DescriptorListLoadingContext,
                                            zipFilePool: ZipFilePool?): List<Deferred<IdeaPluginDescriptorImpl?>>
}

private class PathBasedProductLoadingStrategy : ProductLoadingStrategy() {
  override fun loadBundledPluginDescriptors(scope: CoroutineScope,
                                            bundledPluginDir: Path?,
                                            isUnitTestMode: Boolean,
                                            context: DescriptorListLoadingContext,
                                            zipFilePool: ZipFilePool?): List<Deferred<IdeaPluginDescriptorImpl?>> {
    val effectiveBundledPluginDir = bundledPluginDir ?: if (isUnitTestMode) {
      null
    }
    else {
      Paths.get(PathManager.getPreInstalledPluginsPath())
    }

    return if (effectiveBundledPluginDir == null) {
      emptyList()
    }
    else {
      scope.loadDescriptorsFromDir(dir = effectiveBundledPluginDir, context = context, isBundled = true, pool = zipFilePool)
    }
  }
}  