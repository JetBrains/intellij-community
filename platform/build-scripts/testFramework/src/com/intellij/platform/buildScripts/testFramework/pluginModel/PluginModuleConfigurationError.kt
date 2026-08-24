// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.ide.plugins.DescriptorExclusionReason
import com.intellij.ide.plugins.PluginInitializationDiagnosticUtils
import com.intellij.ide.plugins.shortLogDescription

class PluginModuleConfigurationError(
  val pluginModelModuleName: String,
  val descriptorExclusionReason: DescriptorExclusionReason? = null,
  cause: Throwable? = null,
  errorMessage: String,
) : AssertionError(errorMessage, cause) {
  override fun toString(): String {
    val sb = StringBuilder()
    sb.append("Module: $pluginModelModuleName\n")

    sb.append("${message}\n")
    descriptorExclusionReason?.let { reason ->
      sb.append("  Plugin: ${reason.descriptor.shortLogDescription}\n")
      sb.append("  Reason: ${PluginInitializationDiagnosticUtils.getLogMessage(reason)}\n")
    }
    cause?.let {
      sb.append("  Cause: ${it.message}\n")
    }

    return sb.toString()
  }
}
