// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent

import com.intellij.execution.process.SelfKiller
import com.intellij.platform.eel.EelProcess
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * An adapter for PTY processes created by IJent.
 *
 * See also [IjentChildProcessAdapter].
 */
@ApiStatus.Internal
class IjentChildPtyProcessAdapter(
  coroutineScope: CoroutineScope,
  private val ijentChildProcess: EelProcess,
) : PtyProcess(), SelfKiller {
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

  override fun exitValue(): Int = delegate.exitValue()

  override fun destroy(): Unit = delegate.destroy()

  @RequiresBackgroundThread
  @Throws(InterruptedException::class)
  override fun setWinSize(winSize: WinSize): Unit = delegate.runBlockingInContext {
    // Notice that setWinSize doesn't throw InterruptedException in contrast with many other methods of Process.
    try {
      delegate.ijentChildProcess.resizePty(columns = winSize.columns, rows = winSize.rows)
    }
    catch (err: EelProcess.ResizePtyError) {
      // The other implementation throw IllegalStateException in such cases.
      throw IllegalStateException(err.message, err)
    }
  }

  @Throws(IOException::class)
  override fun getWinSize(): WinSize {
    // The method seems to be unused, while it requires efforts for implementation.
    TODO("Not yet implemented")
  }

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

  override fun tryDestroyGracefully(): Boolean =
    delegate.tryDestroyGracefully()
}