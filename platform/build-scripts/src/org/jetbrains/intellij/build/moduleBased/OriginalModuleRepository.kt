// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.moduleBased

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.ProductModules
import com.intellij.platform.runtime.product.serialization.RawProductModules
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import java.nio.file.Path

/**
 * Provide access to the original runtime module repository generated during compilation.
 * Module descriptors point to output directories with class-files and library JARs in the local Maven repository.
 */
interface OriginalModuleRepository {
  val repositoryPath: Path

  val rawRepositoryData: RawRuntimeModuleRepositoryData
  
  val repository: RuntimeModuleRepository

  /**
   * Loads information about the product layout from product-modules.xml file located in module [rootModuleName], for a product running in
   * [productMode].
   */
  suspend fun loadProductModules(rootModuleName: String, productMode: ProductMode): ProductModules

  /**
   * Loads raw data from product-modules.xml file located in module [rootModuleName], for a product running in [productMode].
   * Unlike [loadProductModules], it doesn't use file from module output directories, so it works even the modules aren't compiled yet.
   */
  fun loadRawProductModules(rootModuleName: String, productMode: ProductMode): RawProductModules
}