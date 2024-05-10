// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.plugins

import com.intellij.util.lang.ZipFilePool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * This class is temporarily added to support two ways of loading the plugin descriptors: [the old one][PathBasedProductLoadingStrategy]
 * which is based on layout of JAR files in the IDE installation directory and [the new one][com.intellij.platform.bootstrap.ModuleBasedProductLoadingStrategy]
 * which uses information from runtime module descriptors.
 */
@ApiStatus.Internal
abstract class ProductLoadingStrategy {
  companion object {
    @Volatile
    private var ourStrategy: ProductLoadingStrategy? = null

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

    /**
     * Creates an instance of the old path-based strategy even if module-based strategy is used in the product.
     * This is needed to load plugin descriptors from another IDE during settings import.
     */
    internal fun createPathBasedLoadingStrategy(): ProductLoadingStrategy = PathBasedProductLoadingStrategy()
  }

  /**
   * Returns ID of current [ProductMode][com.intellij.platform.runtime.product.ProductMode].
   */
  abstract val currentModeId: String

  /**
   * Adds roots of all modules from the main module group and their dependencies to the classpath of [bootstrapClassLoader].
   */
  abstract fun addMainModuleGroupToClassPath(bootstrapClassLoader: ClassLoader)

  abstract fun loadBundledPluginDescriptors(
    scope: CoroutineScope,
    bundledPluginDir: Path?,
    isUnitTestMode: Boolean,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): List<Deferred<IdeaPluginDescriptorImpl?>>

  /** Loads descriptors for custom (non-bundled) plugins from [customPluginDir] */
  abstract fun loadCustomPluginDescriptors(
    scope: CoroutineScope,
    customPluginDir: Path,
    context: DescriptorListLoadingContext,
    zipFilePool: ZipFilePool,
  ): Collection<Deferred<IdeaPluginDescriptorImpl?>>
  
  abstract fun isOptionalProductModule(moduleName: String): Boolean

  /**
   * Returns the path to a JAR or directory containing classes from [moduleName] registered as a content module in the product, or `null`
   * if the mentioned content module isn't present in the distribution.
   */
  abstract fun findProductContentModuleClassesRoot(moduleName: String, moduleDir: Path): Path?

  /**
   * Returns `true` if the loader should search for META-INF/plugin.xml files in the core application classpath and load them.
   */
  abstract val shouldLoadDescriptorsFromCoreClassPath: Boolean
}