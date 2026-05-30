// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.moduleRepository

import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor

object RuntimeModuleRepositoryValidator {
  interface ErrorReporter {
    fun reportError(errorMessage: String)
  }
  
  fun validate(descriptors: List<RawRuntimeModuleDescriptor>, pluginHeaders: List<RuntimePluginHeader>, errorReporter: ErrorReporter) {
    val moduleIDs = HashSet<RuntimeModuleId>()
    for (descriptor in descriptors) {
      if (!moduleIDs.add(descriptor.moduleId)) {
        errorReporter.reportError("Several modules with the same ID '${descriptor.moduleId.displayName}' are registered in the repository")
      }
    }
    val pluginDescriptorModuleIDs = HashSet<RuntimeModuleId>()
    for (header in pluginHeaders) {
      if (!pluginDescriptorModuleIDs.add(header.pluginDescriptorModuleId)) {
        errorReporter.reportError("Several plugin headers with the same plugin descriptor module ID '${header.pluginDescriptorModuleId.displayName}' are registered in the repository")
      }
      for (includedRuntimeModule in header.includedModules) {
        if (includedRuntimeModule.moduleId !in moduleIDs) {
          errorReporter.reportError("""
            |Plugin header for '${header.pluginId}' (plugin descriptor module '${header.pluginDescriptorModuleId.name}') includes module '${includedRuntimeModule.moduleId.displayName}', 
            |which is not registered in the runtime module repository.
            |Most probably it means that '${includedRuntimeModule.moduleId.displayName}' isn't included in the product layout.
          """.trimMargin()
          )
        }
      }
    }
  }
}