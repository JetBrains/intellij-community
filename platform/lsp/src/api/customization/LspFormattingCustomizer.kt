// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock

sealed class LspFormattingCustomizer

open class LspFormattingSupport : LspFormattingCustomizer() {
  /**
   * This function is called on `Reformat Code` action, which might be explicit (e.g., on a shortcut)
   * or implicit (e.g., on save, if formatting on save is enabled).
   *
   * If this function returns `true` then the IDE will send
   * [textDocument/formatting](https://microsoft.github.io/language-server-protocol/specification/#textDocument_formatting)
   * request to the server and apply the received result.
   * IDE's internal formatter (if it exists) won't be used.
   *
   * If this function returns `false` then the IDE will use its internal formatter (if it exists) for this file.
   *
   * Implementation may look like this:
   *
   *      override fun shouldFormatThisFileExclusivelyByServer(file: VirtualFile,
   *                                                           ideCanFormatThisFileItself: Boolean,
   *                                                           serverExplicitlyWantsToFormatThisFile: Boolean) =
   *        file.extension == "foo"
   *
   * @param file a file to format
   *
   * @param ideCanFormatThisFileItself technically, this argument says whether [com.intellij.lang.LanguageFormatting.forContext]
   * returns not null [com.intellij.formatting.FormattingModelBuilder] for this [file]
   *
   * @param serverExplicitlyWantsToFormatThisFile `true` means that the server has sent
   * [client/registerCapability](https://microsoft.github.io/language-server-protocol/specification/#client_registerCapability)
   * request to the IDE to register its `textDocument/formatting` capability, and the [file] matches the provided
   * [DocumentSelector](https://microsoft.github.io/language-server-protocol/specification/#documentSelector).
   * In other words, the server has explicitly said that it wants to format this particular [file].
   * Having only static `documentFormattingProvider`
   * [server capability](https://microsoft.github.io/language-server-protocol/specification/#serverCapabilities)
   * (within the [InitializeResult](https://microsoft.github.io/language-server-protocol/specification/#initializeResult))
   * doesn't count as an explicit wish to format this specific [file]
   */
  @RequiresReadLock
  open fun shouldFormatThisFileExclusivelyByServer(
    file: VirtualFile,
    ideCanFormatThisFileItself: Boolean,
    serverExplicitlyWantsToFormatThisFile: Boolean,
  ): Boolean =
    !ideCanFormatThisFileItself && serverExplicitlyWantsToFormatThisFile
}

object LspFormattingDisabled : LspFormattingCustomizer()
