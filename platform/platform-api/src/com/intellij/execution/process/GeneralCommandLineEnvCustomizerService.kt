// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process

import com.intellij.execution.ExecutionEnvCustomizerService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GeneralCommandLineEnvCustomizerService : ExecutionEnvCustomizerService {
  override fun customizeEnv(commandLine: GeneralCommandLine, environment: MutableMap<String, String>) {
    CommandLineEnvCustomizer.EP_NAME.extensionList.forEach { customizer -> customizer.customizeEnv(commandLine, environment) }
  }
}

@ApiStatus.Experimental
interface CommandLineEnvCustomizer {
  fun customizeEnv(commandLine: GeneralCommandLine, environment: MutableMap<String, String>)

  companion object {
    @ApiStatus.Experimental
    val EP_NAME: ExtensionPointName<CommandLineEnvCustomizer> = ExtensionPointName("com.intellij.commandLineEnvCustomizer")
  }
}