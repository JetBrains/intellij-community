// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.execution.process.SelfKiller
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelProcessManagementApi
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
  private val coroutineScope: CoroutineScope,
  private val ijentChildProcess: EelProcess,
  private val processManagement: EelProcessManagementApi? = null,
) : Process(), SelfKiller {
  private val delegate = IjentChildProcessAdapterDelegate(
    coroutineScope,
    ijentChildProcess,
  )

  override fun toString(): String = "${javaClass.simpleName}($ijentChildProcess)"

  override fun getOutputStream(): OutputStream = delegate.outputStream

  override fun getInputStream(): InputStream = delegate.inputStream

  override fun getErrorStream(): InputStream = delegate.errorStream

  @Throws(InterruptedException::class)
  override fun waitFor(): Int = delegate.waitFor()

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

  /**
   * Returns a [ProcessHandle] for the remote process backed by [ijentChildProcess].
   *
   * Unlike [pid], the handle intentionally exposes the environment-side pid and the process tree of the environment, so that callers
   * can navigate [ProcessHandle.children] / [ProcessHandle.descendants] and terminate subtrees (see the class documentation of
   * [com.intellij.platform.eel.EelProcess] about the difference between local and environment pids).
   */
  override fun toHandle(): ProcessHandle =
    if (processManagement != null)
      IjentChildProcessHandlerAdapter(processManagement, coroutineScope, ijentChildProcess.pid.value, ownProcess = ijentChildProcess, cachedInfo = null)
    else
      super.toHandle()

  override fun onExit(): CompletableFuture<Process> =
    delegate.onExit().thenApply { this }

  override fun exitValue(): Int = delegate.exitValue()

  override fun destroy(): Unit = delegate.destroy()

  override fun tryDestroyGracefully(): Boolean = delegate.tryDestroyGracefully()
}