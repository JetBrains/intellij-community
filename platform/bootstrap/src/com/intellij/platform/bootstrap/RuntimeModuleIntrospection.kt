// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bootstrap

import com.intellij.ide.plugins.ProductLoadingStrategy
import com.intellij.platform.runtime.repository.RuntimeModuleRepository
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Provides access to information about IntelliJ modules available in the current process.
 * If the new modular loading scheme is used, the already loaded instance of the repository is returned. Otherwise, it's lazily loaded
 * on the first access.
 */
object RuntimeModuleIntrospection {
  private var moduleRepositoryValue: RuntimeModuleRepository? = null
  @Volatile
  private var loaded = false

  val moduleRepositoryPath: Path?
    get() {
      val repositoryPath = System.getProperty("intellij.platform.runtime.repository.path")
      return if (repositoryPath != null) Path(repositoryPath) else null
    }
  
  val moduleRepository: RuntimeModuleRepository?
    get() {
      if (!loaded) {
        moduleRepositoryValue = loadModuleRepository()
        loaded = true
      }
      return moduleRepositoryValue
    }

  private fun loadModuleRepository(): RuntimeModuleRepository? {
    val loadingStrategy = ProductLoadingStrategy.strategy
    if (loadingStrategy is ModuleBasedProductLoadingStrategy) {
      return loadingStrategy.moduleRepository
    }
    val repositoryPath = moduleRepositoryPath
    if (repositoryPath != null) {
      return RuntimeModuleRepository.create(repositoryPath)
    }
    return null
  }
}