// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Provides the default settings for one LSP server.
 *
 * The [serverId] must stay stable because it identifies the server configuration.
 */
@ApiStatus.Experimental
interface LspIntegrationSettingsProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<LspIntegrationSettingsProvider> = ExtensionPointName.create("com.intellij.platform.lsp.serverSettingsProvider")
  }

  /** A stable identifier for this server. */
  val serverId: String

  /** The integration provider that starts this server. */
  val integrationProviderClass: Class<out LspIntegrationProvider>

  /** The default configuration used when no project-specific changes exist. */
  val defaultConfiguration: LspPluginServerConfiguration
}

/**
 * Environment variables used to start an LSP server.
 *
 * @param variables custom environment variables. The map is added to the process environment.
 * @param passParentEnvironment whether the process inherits the IDE environment.
 */
@ApiStatus.Experimental
data class LspServerEnvironmentData(
  val variables: Map<String, String> = emptyMap(),
  val passParentEnvironment: Boolean = true,
) {
  /** Applies these environment settings to the command line. */
  fun configureCommandLine(commandLine: GeneralCommandLine): GeneralCommandLine {
    return commandLine
      .withEnvironment(variables)
      .withParentEnvironmentType(
        if (passParentEnvironment) GeneralCommandLine.ParentEnvironmentType.CONSOLE
        else GeneralCommandLine.ParentEnvironmentType.NONE
      )
  }
}

/**
 * The configuration of an LSP server provided by a plugin.
 *
 * The configuration can contain provider defaults or project-specific changes.
 *
 * The class is immutable. To change a value, call [copy].
 *
 * @param name the display name of the server.
 * @param arguments command line arguments. Each list item is one argument.
 * @param filePatterns file name patterns. Use `*` for any number of characters and `?` for one character.
 * For example, `*.lua` matches all Lua files. A pattern matches the file name, not the full path.
 * @param initializationOptions a JSON object sent as the LSP `initialize` request options.
 * @param environmentVariables environment variables for the server process.
 */
@ApiStatus.Experimental
data class LspPluginServerConfiguration(
  @NlsSafe val name: String,
  val arguments: List<String> = emptyList(),
  val filePatterns: List<String> = emptyList(),
  val initializationOptions: String = "",
  val environmentVariables: LspServerEnvironmentData = LspServerEnvironmentData(),
) {
  /** Returns whether the file matches one of the configured file patterns. */
  fun isSupportedFile(file: VirtualFile): Boolean {
    return filePatterns.any { pattern ->
      pattern.isNotBlank() && FileNameMatcherFactory.getInstance().createMatcher(pattern).acceptsCharSequence(file.name)
    }
  }
}
