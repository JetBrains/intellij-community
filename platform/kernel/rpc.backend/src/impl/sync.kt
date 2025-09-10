// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

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
     * @see awaitPatchEngine
     */
    suspend fun awaitDocumentSync() {
      EP_NAME.extensionList.forEach { it.awaitDocumentSync() }
    }
  }
}