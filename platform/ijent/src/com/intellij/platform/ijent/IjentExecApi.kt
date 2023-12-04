// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to an IJent process running on some machine. An instance of this interface gives ability to run commands
 * on a local or a remote machine. Every instance corresponds to a single machine, i.e. unlike Run Targets, if IJent is launched
 * in a Docker container, every call to [executeProcess] runs a command in the same Docker container.
 *
 * Usually, [IjentSessionProvider] creates instances of [IjentExecApi].
 */
@ApiStatus.Experimental
interface IjentExecApi : AutoCloseable {
  val id: IjentId

  val platform: IjentExecFileProvider.SupportedPlatform

  /**
   * Every [IjentId] must have its own child scope. Cancellation of this scope doesn't directly lead to cancellation of any coroutine
   * from the parent job.
   *
   * Cancellation of this scope must lead to termination of the IJent process on the other side.
   */
  val coroutineScope: CoroutineScope

  /**
   * Returns basic info about the process that doesn't change during the lifetime of the process.
   */
  suspend fun info(): Info

  /**
   * Explicitly terminates the process on the remote machine.
   */
  override fun close() {
    coroutineScope.cancel(CancellationException("Closed via Closeable interface"))
    // The javadoc of the method doesn't clarify if the method supposed to wait for the resource destruction.
  }

  val fs: IjentFileSystemApi

  /**
   * Starts a process on a remote machine. Right now, the child process may outlive the instance of IJent.
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * Every successfully started process MUST be destroyed later with [IjentChildProcess.close].
   * Otherwise, it can cause memory leaks on the remote side.
   *
   * Beware that processes with [pty] don't have stderr.
   *
   * By default, environment is always inherited from the running IJent instance, which may be unwanted. [env] allows to alter
   * some environment variables, it doesn't clear the variables from the parent. When the process should be started in an environment like
   * in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [env].
   *
   * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
   * [workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
   */
  suspend fun executeProcess(
    exe: String,
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    pty: Pty? = null,
    workingDirectory: String? = null,
  ): ExecuteProcessResult

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  suspend fun fetchLoginShellEnvVariables(): Map<String, String>

  /**
   * Creates a remote UNIX socket forwarding, i.e. IJent listens waits for a connection on the remote machine, and when the connection
   * is accepted, the IDE communicates to the remote client via a pair of Kotlin channels.
   *
   * The call accepts only one connection. If multiple connections should be accepted, the function is supposed to be called in a loop:
   * ```kotlin
   * val ijent: IjentApi = ijentApiFactory()
   *
   * val (socketPath, tx, rx) = listenOnUnixSocket(CreateFilePath.MkTemp(prefix = "ijent-", suffix = ".sock"))
   * println(socketPath) // /tmp/ijent-12345678.sock
   * launch {
   *   handleConnection(tx, rx)
   * }
   * while (true) {
   *   val (_, tx, rx) = listenOnUnixSocket(CreateFilePath.Fixed(socketPath))
   *   launch {
   *     handleConnection(tx, rx)
   *   }
   * }
   * ```
   */
  suspend fun listenOnUnixSocket(path: CreateFilePath = CreateFilePath.MkTemp()): ListenOnUnixSocketResult

  /**
   * On Unix-like OS, PID is int32. On Windows, PID is uint32. The type of Long covers both PID types, and a separate class doesn't allow
   * to forget that fact and misuse types in APIs.
   */
  data class Pid(val value: Long) {
    override fun toString(): String = value.toString()
  }

  /**
   * [remotePid] is a process ID of IJent running on the remote machine.
   */
  data class Info(
    val remotePid: Pid,
  )

  data class ListenOnUnixSocketResult(
    val unixSocketPath: String,
    // TODO Avoid excessive byte arrays copying.
    val tx: SendChannel<ByteArray>,
    val rx: ReceiveChannel<ByteArray>,
  )

  sealed interface ExecuteProcessResult {
    class Success(val process: IjentChildProcess) : ExecuteProcessResult
    data class Failure(val errno: Int, val message: String) : ExecuteProcessResult
  }

  sealed interface CreateFilePath {
    data class Fixed(val path: String) : CreateFilePath

    /** When [directory] is empty, the usual tmpdir is used. */
    data class MkTemp(val directory: String = "", val prefix: String = "", val suffix: String = "") : CreateFilePath
  }

  /** [echo] must be true in general and must be false when the user is asked for a password. */
  data class Pty(val columns: Int, val rows: Int, val echo: Boolean)
}
