// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import org.jetbrains.annotations.ApiStatus

/**
 * This is a marker interface for [AsyncFileListener.ChangeApplier] that makes the "after" event to be executed before
 * "after" events of all [com.intellij.openapi.vfs.newvfs.BulkFileListener].
 */
@ApiStatus.Internal
interface AfterEventShouldBeFiredBeforeOtherListeners