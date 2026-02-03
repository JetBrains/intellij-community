// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.util.concurrency.AppExecutorUtil
import com.jediterm.terminal.TerminalExecutorServiceManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

class TerminalExecutorServiceManagerImpl : TerminalExecutorServiceManager {
  private val singleThreadScheduledExecutor: ScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService("Terminal-fast-job", 1)

  override fun getSingleThreadScheduledExecutor(): ScheduledExecutorService = singleThreadScheduledExecutor

  override fun getUnboundedExecutorService(): ExecutorService = AppExecutorUtil.getAppExecutorService()

  override fun shutdownWhenAllExecuted() {
    // Let queued tasks finish on their own. No need to shut down the application pool.
  }
}
