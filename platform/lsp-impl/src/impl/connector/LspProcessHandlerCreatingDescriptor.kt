package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Temporary solution for internal use: `QmlServerDescriptor` wants to create [com.intellij.execution.process.BaseProcessHandler]
 * but [com.intellij.platform.lsp.api.LspServerDescriptor.startServerProcess] requires `OSProcessHandler` for historical reasons.
 */
@ApiStatus.Internal
interface LspProcessHandlerCreatingDescriptor {
  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  fun createProcessHandler(): BaseProcessHandler<*>
}
