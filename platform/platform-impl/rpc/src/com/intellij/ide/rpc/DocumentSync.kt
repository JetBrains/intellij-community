// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.ide.rpc

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Waits until the backend documents have the frontend changes that the user made before the current RPC call.
 *
 * Call it in a backend RPC handler before the handler uses an offset or a range from a frontend document.
 * Call it in the coroutine of the handler, or in a child coroutine of it. The function reads the sync data from the
 * coroutine context of the call. In a coroutine outside the call, for example one launched in a service scope, it
 * returns at once.
 *
 * In split mode, the function waits until the backend applies the document patches that the frontend queued before
 * the call. In the monolith, frontend and backend share the documents, and the function returns at once.
 *
 * The function does not commit PSI. Use [com.intellij.openapi.application.ReadConstraint.withDocumentsCommitted] or
 * [com.intellij.psi.PsiDocumentManager] for that.
 *
 * @throws kotlinx.coroutines.TimeoutCancellationException if the changes do not arrive in 30 seconds.
 */
suspend fun awaitDocumentSync() {
  DocumentSync.awaitDocumentSync()
}

@ApiStatus.NonExtendable
@ApiStatus.Internal
interface DocumentSync {
  suspend fun awaitDocumentSync()

  companion object {
    private val EP_NAME = ExtensionPointName<DocumentSync>("com.intellij.platform.rpc.backend.documentSync")

    /**
     * Await the last document updates from the frontend.
     *
     * In split mode, awaits the completion of the last queued patch engine request.
     * In monolith mode, does nothing.
     *
     * This method is supposed to be called in the backend RPC handler.
     *
     * @see com.jetbrains.rdserver.requests.awaitPatchEngine
     * @see com.intellij.ide.rpc.awaitDocumentSync
     */
    suspend fun awaitDocumentSync() {
      EP_NAME.extensionList.forEach { it.awaitDocumentSync() }
    }
  }
}
