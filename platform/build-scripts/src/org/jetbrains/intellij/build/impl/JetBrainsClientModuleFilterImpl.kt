// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.repository.RuntimeModuleId
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JetBrainsClientModuleFilter

class JetBrainsClientModuleFilterImpl(clientMainModuleName: String, context: BuildContext): JetBrainsClientModuleFilter {
  private val includedModules: Set<RuntimeModuleId>
  
  init {
    val productModules = context.originalModuleRepository.loadProductModules(clientMainModuleName, ProductMode.FRONTEND)
    includedModules = (sequenceOf(productModules.mainModuleGroup) + productModules.bundledPluginModuleGroups.asSequence())
       .flatMap { it.includedModules.asSequence() } 
       .filter { included -> included.moduleDescriptor.moduleId !in MODULES_SCRAMBLED_WITH_FRONTEND }
       .mapTo(HashSet()) { it.moduleDescriptor.moduleId }
  }

  override fun isModuleIncluded(moduleName: String): Boolean {
    return RuntimeModuleId.module(moduleName) in includedModules
  }

  override fun isProjectLibraryIncluded(libraryName: String): Boolean {
    return RuntimeModuleId.projectLibrary(libraryName) in includedModules
  }
}

const val PLATFORM_MODULE_SCRAMBLED_WITH_FRONTEND: String = "intellij.platform.commercial.license"
val PROJECT_LIBRARIES_SCRAMBLED_WITH_FRONTEND: Set<String> = setOf(
  "LicenseServerAPI",
  "LicenseDecoder",
)

/**
 * Contains a set of runtime modules from the platform part which are also included in the frontend JARs and scrambled (differently) there.
 * It's important not to include JARs for these modules in the platform part to the classpath of the frontend process, because they may 
 * cause clashes.
 */
val MODULES_SCRAMBLED_WITH_FRONTEND: Set<RuntimeModuleId> by lazy {
  setOf(RuntimeModuleId.module(PLATFORM_MODULE_SCRAMBLED_WITH_FRONTEND)) + 
  PROJECT_LIBRARIES_SCRAMBLED_WITH_FRONTEND.map { RuntimeModuleId.projectLibrary(it) }
}

object EmptyJetBrainsClientModuleFilter : JetBrainsClientModuleFilter {
  override fun isModuleIncluded(moduleName: String): Boolean = false
  override fun isProjectLibraryIncluded(libraryName: String): Boolean = false
}