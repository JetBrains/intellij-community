// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.platform.rpc.backend

import org.jetbrains.annotations.ApiStatus
import com.intellij.ide.rpc.awaitDocumentSync as ideAwaitDocumentSync

/**
 * Moved to `intellij.platform.ide.rpc`, which loads in every product mode.
 */
@Deprecated("Use com.intellij.ide.rpc.awaitDocumentSync", ReplaceWith("awaitDocumentSync()", "com.intellij.ide.rpc.awaitDocumentSync"))
suspend fun awaitDocumentSync() {
  ideAwaitDocumentSync()
}
