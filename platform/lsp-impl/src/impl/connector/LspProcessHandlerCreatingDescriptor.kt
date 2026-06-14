package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal interface LspProcessHandlerCreatingDescriptor {
  @RequiresBackgroundThread
  @Throws(ExecutionException::class)
  fun createProcessHandler(): BaseProcessHandler<*>
}
