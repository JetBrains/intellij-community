// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.Key
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import com.intellij.psi.impl.FreeThreadedFileViewProvider
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean


private val IN_COMA = Key.create<Boolean>("IN_COMA")
private val LOG = fileLogger()

private val isBulkInvalidationNeeded: AtomicBoolean = AtomicBoolean(false)

@ApiStatus.Internal
fun signalBulkInvalidationNeeded() {
  isBulkInvalidationNeeded.set(true)
}

internal fun pollInvalidationAfterPropertyPush(): Boolean {
  ThreadingAssertions.assertWriteAccess()
  return isBulkInvalidationNeeded.getAndSet(false)
}

internal fun FileViewProvider.markPossiblyInvalidated() {
  LOG.assertTrue(this !is FreeThreadedFileViewProvider)
  putUserData(IN_COMA, true)
  (this as AbstractFileViewProvider).markPossiblyInvalidated()
  FileManagerImpl.clearPsiCaches(this)
}

internal fun FileViewProvider.unmarkPossiblyInvalidated() {
  putUserData(IN_COMA, null)
}

internal fun FileViewProvider.isPossiblyInvalidated(): Boolean {
  return getUserData(IN_COMA) != null ||
         getFileProviderMap()?.isPossiblyInvalidated == true
}
