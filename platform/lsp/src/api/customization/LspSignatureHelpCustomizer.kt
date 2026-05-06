// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

sealed class LspSignatureHelpCustomizer

/**
 * Handles [SignatureHelp](https://microsoft.github.io/language-server-protocol/specification#signatureHelp) objects
 * received from the LSP server.
 * Implementations may fine-tune the signature help behavior.
 */
open class LspSignatureHelpSupport : LspSignatureHelpCustomizer() {

  /**
   * This function is called when the user types [charTyped] in the editor,
   * but only if the typed character is listed in the server capabilities as one of the characters that should trigger signature help
   * ([SignatureHelpOptions.triggerCharacters](https://microsoft.github.io/language-server-protocol/specification/#signatureHelpOptions)).
   * According to the LSP specification, the IDE should initiate a signature help session when such a character is typed.
   *
   * By default, this function returns `true`, which triggers a signature help session.
   *
   * Implementations may override this function and return `false` to ignore the presence of the typed character
   * in the server's `SignatureHelpOptions.triggerCharacters`.
   */
  open fun isTriggerCharacterRespected(charTyped: Char): Boolean = true
}

object LspSignatureHelpDisabled : LspSignatureHelpCustomizer()