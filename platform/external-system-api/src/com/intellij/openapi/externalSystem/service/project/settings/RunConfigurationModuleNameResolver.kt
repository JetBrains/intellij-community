// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module


/**
 * Helper service for importing Run Configurations configs.
 *
 * Allows to bridge module names between external build tools configuration data and IDEA internal model.
 */
@Service
class RunConfigurationModuleNameResolverService {
  /**
   * Finds a module for the given [moduleName].
   * Uses [RunConfigurationModuleNameResolver] EP when needed.
   * @return the resolved module, or `null` if [moduleName] is `null`/blank or no strategy could resolve it.
   */
  fun findModule(modelsProvider: IdeModifiableModelsProvider, moduleName: String?): Module? {
    if (moduleName.isNullOrBlank()) return null
    modelsProvider.modifiableModuleModel.findModuleByName(moduleName)?.let { return it }
    return RunConfigurationModuleNameResolver.EP_NAME.computeSafeIfAny {
      it.resolveModule(modelsProvider, moduleName)
    }
  }
}

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
  }
}
