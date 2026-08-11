// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

/**
 * A handle to a process started with [EelExecApi.spawnProcess], running inside the environment of the [EelExecApi] that launched it.
 *
 * A process runs in one of two I/O modes, fixed at spawn time by [EelExecApi.ExecuteProcessOptions.interactionOptions]:
 * - **Pipe mode** (the default, or [EelExecApi.RedirectStdErr]): [stdin], [stdout], and [stderr] are independent byte streams;
 *   [stderr] may be redirected to [stdout] or discarded, otherwise the three are separate.
 * - **PTY mode** ([EelExecApi.Pty]): the process is attached to a pseudo-terminal. [stdout] carries the terminal output and [stderr]
 *   is merged into it (so [stderr] stays closed); terminal line discipline and echo apply, input may need escape codes and a `TERM`
 *   variable, and only here does [resizePty] work.
 *
 * [exitCode] completes when the process terminates, and [convertToJavaProcess] adapts the handle to the JVM [Process] API.
 *
 * The interface models both POSIX ([EelPosixProcess]) and Windows ([EelWindowsProcess]) processes. They diverge mainly in process
 * control: the signals sent by [kill] and [interrupt] differ per OS (see each), and graceful shutdown via [EelPosixProcess.terminate]
 * (`SIGTERM`) exists only on POSIX. Both support PTY mode.
 */
@ApiStatus.Experimental
sealed interface EelProcess {
  /** The process identifier in the environment. */
  @get:ApiStatus.Experimental
  val pid: EelApi.Pid

  /**
   * The process's standard input. Each chunk is flushed toward the process immediately, with no intermediate buffer.
   *
   * Writing may suspend while back-pressure is applied. A completed write means only that the data was accepted for delivery — not that
   * the process has received or read it.
   */
  @get:ApiStatus.Experimental
  val stdin: EelSendChannel

  /** The process's standard output (in PTY mode, also the merged standard error — see above). */
  @get:ApiStatus.Experimental
  val stdout: EelReceiveChannel

  /** The process's standard error: an independent stream in pipe mode, closed and merged into [stdout] under a PTY (see above). */
  @get:ApiStatus.Experimental
  val stderr: EelReceiveChannel

  /** Completes with the process's exit code once it terminates. See [SafeDeferred]. */
  @get:ApiStatus.Experimental
  val exitCode: SafeDeferred<Int>

  /**
   * Sends `SIGKILL` on Unix.
   *
   * Calls [`TerminateProcess`](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-terminateprocess)
   * on Windows.
   */
  @ApiStatus.Experimental
  suspend fun kill()

  /**
   * Sends `SIGINT` on Unix.
   * Sends `CTRL+C` on Windows by attaching a console.
   *
   * Warning: This signal can be ignored.
   */
  @ApiStatus.Experimental
  suspend fun interrupt()

  /**
   * Converts to the JVM [Process] which can be used instead of [EelProcess] for compatibility reasons.
   * Note: After conversion, this [EelProcess] shouldn't be used: Use result [Process] instead
   * If the process was launched with PTY, `com.pty4j.PtyProcess` instance is returned.
   */
  @ApiStatus.Experimental
  fun convertToJavaProcess(): Process

  /**
   * Resizes the pseudo-terminal to [columns] columns by [rows] rows. PTY mode only (see above).
   *
   * @throws ResizePtyError if there is no PTY ([ResizePtyError.NoPty]), the process already exited ([ResizePtyError.ProcessExited]),
   *   or the OS call fails ([ResizePtyError.Errno]).
   */
  @Throws(ResizePtyError::class)  // Can't use @CheckReturnValue: KTIJ-7061
  @ApiStatus.Experimental
  suspend fun resizePty(columns: Int, rows: Int)

  /** Failure of [resizePty]. */
  @ApiStatus.Experimental
  sealed class ResizePtyError(msg: String) : Exception(msg) {
    class ProcessExited : ResizePtyError("Process exited")
    class NoPty : ResizePtyError("Process has no PTY")
    data class Errno(val errno: Int, override val message: String) : ResizePtyError("[$errno] $message")
  }
}

/**
 * An [EelProcess] in a POSIX environment, returned by [EelExecPosixApi.spawnProcess].
 *
 * Beyond the common surface it adds [terminate] (`SIGTERM`) — graceful, cooperative shutdown that a process can handle or ignore,
 * for which Windows has no direct equivalent.
 */
@ApiStatus.Experimental
interface EelPosixProcess : EelProcess {
  /**
   * Requests graceful shutdown by sending `SIGTERM`, which the process may handle or ignore. Use [kill] to force termination.
   */
  @ApiStatus.Experimental
  suspend fun terminate()
}

/**
 * An [EelProcess] in a Windows environment, returned by [EelExecWindowsApi.spawnProcess].
 *
 * It currently adds nothing beyond [EelProcess]: Windows has no direct counterpart to POSIX's graceful `SIGTERM`, so there is no
 * `terminate` here — use [interrupt] (CTRL+C) or [kill]. The separate type exists for symmetry with [EelPosixProcess] and to host
 * future Windows-specific operations.
 */
@ApiStatus.Experimental
interface EelWindowsProcess : EelProcess {
  // Nothing yet.
}