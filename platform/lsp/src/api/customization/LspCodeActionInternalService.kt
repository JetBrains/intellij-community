// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.platform.lsp.api.LspClient
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.eclipse.lsp4j.CodeAction
import org.jetbrains.annotations.ApiStatus

/**
 * Lets [LspIntentionAction] resolve a [CodeAction] through the implementation module.
 * The implementation module knows the static and the dynamic server capabilities.
 */
@ApiStatus.Internal
abstract class LspCodeActionInternalService {
  /**
   * Sends the [codeAction/resolve](https://microsoft.github.io/language-server-protocol/specification/#codeAction_resolve) request
   * if the server supports it at the time of the call.
   *
   * @return the resolved [CodeAction], or `null` if the server does not support the request or the request fails
   */
  @RequiresBackgroundThread
  abstract fun resolveCodeAction(lspClient: LspClient, codeAction: CodeAction): CodeAction?
}
