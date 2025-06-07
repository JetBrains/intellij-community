// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.execution.process.SelfKiller
import com.intellij.platform.eel.EelProcess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * An adapter for non-PTY processes created by IJent.
 *
 * See also [IjentChildPtyProcessAdapter].
 */
@ApiStatus.Internal
class IjentChildProcessAdapter(
  coroutineScope: CoroutineScope,
  private val ijentChildProcess: EelProcess,
) : Process(), SelfKiller {
  private val delegate = IjentChildProcessAdapterDelegate(
    coroutineScope,
    ijentChildProcess,
  )

  override fun toString(): String = "${javaClass.simpleName}($ijentChildProcess)"

  override fun getOutputStream(): OutputStream = delegate.outputStream

  override fun getInputStream(): InputStream = delegate.inputStream

  override fun getErrorStream(): InputStream = delegate.errorStream

  @RequiresBackgroundThread
  @Throws(InterruptedException::class)
  override fun waitFor(): Int = delegate.waitFor()

  @RequiresBackgroundThread
  @Throws(InterruptedException::class)
  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = delegate.waitFor(timeout, unit)

  override fun destroyForcibly(): Process = apply { delegate.destroyForcibly() }

  override fun supportsNormalTermination(): Boolean = delegate.supportsNormalTermination()

  override fun isAlive(): Boolean = delegate.isAlive()

  /**
   * It's important to NOT return the real pid, even though it's known. The API of [Process] assumes that the process runs locally,
   * and the pid may be used for manipulations with a process running on a wrong machine.
   * Users who know that it's a remote process are welcome to use [ijentChildProcess] directly.
   */
  override fun pid(): Long =
    throw UnsupportedOperationException()

  override fun onExit(): CompletableFuture<Process> =
    delegate.onExit().thenApply { this }

  override fun exitValue(): Int = delegate.exitValue()

  override fun destroy(): Unit = delegate.destroy()

  override fun tryDestroyGracefully(): Boolean = delegate.tryDestroyGracefully()
}