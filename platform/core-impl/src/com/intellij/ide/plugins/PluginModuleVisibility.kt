// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
object PluginModuleVisibility {
  private val pluginModuleVisibilityCheck by lazy {
    when (System.getProperty("intellij.platform.plugin.modules.check.visibility")) {
      "warning" -> PluginModuleVisibilityCheckOption.REPORT_WARNING
      "error" -> PluginModuleVisibilityCheckOption.REPORT_ERROR
      "disabled" -> PluginModuleVisibilityCheckOption.DISABLED
      else -> PluginModuleVisibilityCheckOption.REPORT_ERROR
    }
  }

  @VisibleForTesting
  fun checkVisibilityAndReturnErrorMessage(sourceModule: PluginModuleDescriptor, targetModule: ContentModuleDescriptor): String? {
    if (pluginModuleVisibilityCheck == PluginModuleVisibilityCheckOption.DISABLED) {
      return null
    }

    val errorMessage = when (targetModule.visibility) {
      ModuleVisibility.PUBLIC -> null
      ModuleVisibility.INTERNAL -> {
        if (targetModule.moduleId.namespace == sourceModule.namespace) null
        else {
          val sourceNamespace = sourceModule.namespace?.let { "is from namespace '$it'" } ?: "has no namespace specified"
          "it $sourceNamespace and depends on module '${targetModule.contentModuleName}' which is registered in '${targetModule.parent.pluginId}' plugin with internal visibility in namespace '${targetModule.moduleId.namespace}'"
        }
      }
      ModuleVisibility.PRIVATE -> {
        if (sourceModule.pluginId == targetModule.pluginId) null
        else "it depends on module '${targetModule.contentModuleName}' which has private visibility in '${targetModule.pluginId}' plugin"
      }
    }
    if (errorMessage == null) {
      return null
    }

    val sourceModuleId = sourceModule.contentModuleName ?: sourceModule.pluginId
    return when (pluginModuleVisibilityCheck) {
      PluginModuleVisibilityCheckOption.REPORT_WARNING -> {
        PluginManagerCore.logger.warn("$sourceModuleId has accessibility problem which is currently ignored: $errorMessage")
        null
      }
      PluginModuleVisibilityCheckOption.REPORT_ERROR -> {
        PluginManagerCore.logger.error(PluginException("$sourceModuleId isn't loaded: $errorMessage", sourceModule.pluginId))
        errorMessage
      }
      PluginModuleVisibilityCheckOption.DISABLED -> null
    }
  }


  private enum class PluginModuleVisibilityCheckOption {
    /** No visibility checks performed */
    DISABLED,

    /** If a module depends on a module which is not visible to it, it's loaded and a warning is printed to the log */
    REPORT_WARNING,

    /** If a module depends on a module which is not visible to it, it's not loaded and an error is printed to the log */
    REPORT_ERROR,
  }

  private val PluginModuleDescriptor.namespace: String?
    get() = when (this) {
      is ContentModuleDescriptor -> moduleId.namespace
      is PluginMainDescriptor -> implicitNamespaceForPluginDescriptorModule
    }
}

