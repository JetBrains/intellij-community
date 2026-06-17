// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServer
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import java.awt.event.MouseEvent

/**
 * See LSP specification: [Code Lens](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeLens).
 * The corresponding feature name in the IDE is "Code Vision".
 */
sealed class LspCodeLensCustomizer

open class LspCodeLensSupport : LspCodeLensCustomizer() {
  /**
   * Returns true if the IDE should ask the server for code lenses for the given file.
   */
  open fun shouldAskServerForCodeLenses(file: VirtualFile): Boolean = true

  /**
   * Returns true if the given code lens should be displayed in the editor.
   */
  open fun shouldDisplayCodeLens(file: VirtualFile, codeLens: CodeLens): Boolean = true

  /**
   * Called when the user clicks on a code lens (code vision item) in the editor.
   *
   * This method is invoked when a user interacts with a code lens that was previously provided
   * by the LSP server via `textDocument/codeLens` requests. The default implementation delegates
   * command execution to [LspCommandsSupport.executeCommand] if available.
   *
   * ## Usage Example
   *
   * Override this method to handle language-specific code lens commands.
   * The utility functions [com.intellij.platform.lsp.api.Lsp4jService.extractLocationsFromJson] and
   * [com.intellij.platform.lsp.util.navigateOrShowPopup] are provided to handle the most common cases.
   * However, since each language and technology has unique requirements, you can implement custom logic
   * tailored to your LSP server's commands, using the utilities as a reference.
   *
   * ```kotlin
   * override fun codeLensClicked(
   *   lspClient: LspClient,
   *   contextFile: VirtualFile,
   *   command: Command,
   *   mouseEvent: MouseEvent?
   * ) {
   *   when (command.command) {
   *     // Show references: display a popup with clickable locations
   *     "language.showReferences" -> {
   *       val locations = Lsp4jService.getInstance().extractLocationsFromJson(command.arguments) ?: return
   *       navigateOrShowPopup(lspClient, locations, "References", mouseEvent)
   *     }
   *
   *     // Run: execute a run configuration based on command arguments
   *     "language.run" -> {
   *       val runnableArgs = command.arguments[0] as? JsonObject
   *       val args = runnableArgs?.get("args")?.asJsonArray
   *       executeRunConfiguration(lspClient.project, args, isDebug = false)
   *     }
   *
   *     // Test: run tests based on command arguments
   *     "language.test" -> {
   *       val testArgs = command.arguments[0] as? JsonObject
   *       val testName = testArgs?.get("testName")?.asString
   *       executeTestConfiguration(lspClient.project, testName)
   *     }
   *   }
   * }
   * ```
   *
   *
   * @param lspClient The LSP client instance that provided this code lens
   * @param contextFile The file where the code lens appears
   * @param command The LSP command to execute, containing command name and arguments
   * @param mouseEvent The mouse event that triggered the click
   *
   * @see LspCommandsSupport.executeCommand
   * @see com.intellij.platform.lsp.api.Lsp4jService.extractLocationsFromJson
   * @see com.intellij.platform.lsp.util.navigateOrShowPopup
   */
  open fun codeLensClicked(lspClient: LspClient, contextFile: VirtualFile, command: Command, mouseEvent: MouseEvent?) {
    @Suppress("DEPRECATION")
    codeLensClicked(lspClient as LspServer, contextFile, command, mouseEvent)
  }

  @Deprecated(
    "Override or call codeLensClicked(lspClient, …) — the LspClient overload",
    ReplaceWith("codeLensClicked(server as LspClient, contextFile, command, mouseEvent)", "com.intellij.platform.lsp.api.LspClient"),
  )
  @Suppress("DEPRECATION")
  open fun codeLensClicked(server: LspServer, contextFile: VirtualFile, command: Command, mouseEvent: MouseEvent?) {
    val commandsCustomizer = server.descriptor.lspCustomization.commandsCustomizer
    if (commandsCustomizer is LspCommandsSupport) {
      commandsCustomizer.executeCommand(server as LspClient, contextFile, command)
    }
  }
}

object LspCodeLensDisabled : LspCodeLensCustomizer()
