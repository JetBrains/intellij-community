// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

import com.intellij.ide.plugins.PluginLoadingError

class PluginModuleConfigurationError(
  val pluginModelModuleName: String,
  val pluginLoadingError: PluginLoadingError? = null,
  errorMessage: String,
) : AssertionError(errorMessage, pluginLoadingError?.error) {
  override fun toString(): String {
    val sb = StringBuilder()
    sb.append("Module: $pluginModelModuleName\n")

    if (pluginLoadingError == null) {
      sb.append("  ${message}\n")
      return sb.toString()
    }

    // Add HTML message as plain text
    sb.append("  Message: ${pluginLoadingError.htmlMessage}\n")

    // Add structured error details
    val reason = pluginLoadingError.reason
    if (reason != null) {
      sb.append("  Plugin     : ${reason.plugin.name} (${reason.plugin.pluginId})\n")
      sb.append("  Log Message: ${reason.logMessage}\n")
      sb.append("  Detailed   : ${reason.detailedMessage}\n")
      sb.append("  Short      : ${reason.shortMessage}\n")
    }

    // Add original error if available
    pluginLoadingError.error?.let {
      sb.append("  Cause: ${it.message}\n")
    }

    return sb.toString()
  }
}