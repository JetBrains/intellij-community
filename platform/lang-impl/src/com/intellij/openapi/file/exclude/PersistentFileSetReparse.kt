// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PersistentFileSetReparse")

package com.intellij.openapi.file.exclude

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.impl.TestOnlyThreading
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType
import com.intellij.util.FileContentUtilCore
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.TimeUnit

/**
 * Clears the file type cache and reparses [files] in [coroutineScope], not on the EDT.
 */
internal fun scheduleReparse(coroutineScope: CoroutineScope, files: Collection<VirtualFile>) {
  coroutineScope.launch {
    backgroundWriteAction {
      CachedFileType.clearCache()
      FileContentUtilCore.reparseFiles(files)
    }
  }
}

/**
 * Waits until the reparse that [scheduleReparse] started in [coroutineScope] ends.
 * The function releases the write-intent lock and dispatches the EDT events while it waits.
 * The background write action of the reparse needs this.
 */
@TestOnly
@RequiresEdt
internal fun waitForReparse(coroutineScope: CoroutineScope) {
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
  TestOnlyThreading.releaseTheAcquiredWriteIntentLockThenExecuteActionAndTakeWriteIntentLockBack {
    while (coroutineScope.coroutineContext.job.children.any { it.isActive }) {
      check(System.nanoTime() < deadline) { "The reparse did not end in 30 seconds" }
      UIUtil.dispatchAllInvocationEvents()
      Thread.sleep(1)
    }
  }
}
