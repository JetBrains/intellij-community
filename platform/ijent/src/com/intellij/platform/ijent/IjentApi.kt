// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

interface IjentApi : AutoCloseable {
  val id: IjentId

  val platform: IjentExecFileProvider.SupportedPlatform

  /**
   * Every [IjentId] must have its own child scope. Cancellation of this scope doesn't directly lead to cancellation of any coroutine
   * from the parent job.
   *
   * Cancellation of this scope must lead to termination of the IJent process on the other side.
   */
  val coroutineScope: CoroutineScope

  override fun close() {
    coroutineScope.cancel(CancellationException("Closed via Closeable interface"))
    // The javadoc of the method doesn't clarify if the method supposed to wait for the resource destruction.
  }

  val fs: IjentFileSystemApi

  suspend fun executeProcess(
    exe: String,
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    pty: Pty? = null,
    workingDirectory: String? = null,
  ): ExecuteProcessResult

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

  data class Pty(val columns: Int, val rows: Int, val echo: Boolean)
}
