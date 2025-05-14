// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.pluginModel

class PluginModuleConfigurationError(
  val moduleName: String,
  errorMessage: String,
  cause: Throwable? = null,
) : AssertionError(errorMessage, cause)