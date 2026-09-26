// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module

/**
 * Extension point that lets a build system contribute a custom strategy for resolving an IDE [Module]
 * from a module name emitted by an external build tool (e.g. Gradle's `idea-ext` plugin).
 */
interface RunConfigurationModuleNameResolver {
  /**
   * Tries to find a module matching [moduleName] according to this strategy.
   *
   * @param modelsProvider the modifiable IDE project structure model, providing access to all modules.
   * @param moduleName     the module name requested by the external build tool, which was not found by a direct lookup.
   * @return the matching module, or `null` if this strategy cannot resolve a (unique) module.
   */
  fun resolveModule(modelsProvider: IdeModifiableModelsProvider, moduleName: String): Module?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<RunConfigurationModuleNameResolver> =
      ExtensionPointName.create("com.intellij.externalSystem.runConfigurationModuleNameResolver")

    @JvmStatic
    fun findModule(modelsProvider: IdeModifiableModelsProvider, moduleName: String?): Module? {
      if (moduleName.isNullOrBlank()) return null
      modelsProvider.modifiableModuleModel.findModuleByName(moduleName)?.let { return it }
      return EP_NAME.computeSafeIfAny {
        it.resolveModule(modelsProvider, moduleName)
      }
    }
  }
}
