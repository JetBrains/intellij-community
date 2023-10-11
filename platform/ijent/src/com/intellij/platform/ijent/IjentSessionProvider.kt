// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.util.attachAsChildTo
import com.intellij.util.namedChildScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Given that there is some IJent process launched, this extension gets handles to stdin+stdout of the process and returns
 * an [IjentApi] instance for calling procedures on IJent side.
 */
interface IjentSessionProvider {
  @get:OverrideOnly
  val epCoroutineScope: CoroutineScope

  /**
   * When calling the method, there's no need to wire [communicationCoroutineScope] to [epCoroutineScope],
   * since it is already performed by factory methods.
   *
   * Automatically registers the result in [IjentSessionRegistry].
   */
  @OverrideOnly
  suspend fun connect(
    id: IjentId,
    communicationCoroutineScope: CoroutineScope,
    platform: IjentExecFileProvider.SupportedPlatform,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi

  companion object {
    private val LOG = logger<IjentSessionProvider>()

    /**
     * The session exits when one of the following happens:
     * * The job corresponding to [communicationCoroutineScope] is finished.
     * * [epCoroutineScope] is finished.
     * * [inputStream] is closed.
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(
      communicationCoroutineScope: CoroutineScope,
      platform: IjentExecFileProvider.SupportedPlatform,
      process: Process,
    ): IjentApi {
      val provider = serviceAsync<IjentSessionProvider>()
      val ijentsRegistry = IjentSessionRegistry.instanceAsync()
      val ijentId = ijentsRegistry.makeNewId()
      val epCoroutineScope = provider.epCoroutineScope
      val childScope = communicationCoroutineScope
        .namedChildScope(ijentId.toString(), supervisor = false)
        .apply { attachAsChildTo(epCoroutineScope) }

      childScope.coroutineContext.job.invokeOnCompletion {
        ijentsRegistry.ijentsInternal.remove(ijentId)
      }

      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$ijentId > watchdog")) {
        while (true) {
          if (process.waitFor(10, TimeUnit.MILLISECONDS)) {
            val exitValue = process.exitValue()
            LOG.debug { "$ijentId exit code $exitValue" }
            check(exitValue == 0) { "Process has exited with code $exitValue" }
            cancel()
            break
          }
          delay(100)
        }
      }
      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$ijentId > finalizer")) {
        try {
          awaitCancellation()
        }
        catch (err: Exception) {
          LOG.debug(err) { "$ijentId is going to be terminated due to receiving an error" }
          throw err
        }
        finally {
          if (process.isAlive) {
            GlobalScope.launch(Dispatchers.IO + coroutineNameAppended("actual destruction")) {
              try {
                if (process.waitFor(5, TimeUnit.SECONDS)) {
                  LOG.debug { "$ijentId exit code ${process.exitValue()}" }
                }
              }
              finally {
                if (process.isAlive) {
                  LOG.debug { "The process $ijentId is still alive, it will be killed" }
                  process.destroy()
                }
              }
            }
            GlobalScope.launch(Dispatchers.IO) {
              LOG.debug { "Closing stdin of $ijentId" }
              process.outputStream.close()
            }
          }
        }
      }

      val processScopeNamePrefix = childScope.coroutineContext[CoroutineName]?.let { "$it >" } ?: ""

      epCoroutineScope.launch(Dispatchers.IO) {
        withContext(coroutineNameAppended("$processScopeNamePrefix $ijentId > logger")) {
          process.errorReader().use { errorReader ->
            for (line in errorReader.lineSequence()) {
              // TODO It works incorrectly with multiline log messages.
              when (line.splitToSequence(Regex(" +")).drop(1).take(1).firstOrNull()) {
                "TRACE" -> LOG.trace { "$ijentId log: $line" }
                "DEBUG" -> LOG.debug { "$ijentId log: $line" }
                "INFO" -> LOG.info("$ijentId log: $line")
                "WARN" -> LOG.warn("$ijentId log: $line")
                "ERROR" -> LOG.error("$ijentId log: $line")
                else -> LOG.trace { "$ijentId log: $line" }
              }
              yield()
            }
          }
        }
      }

      val result = provider.connect(ijentId, childScope, platform, process.inputStream, process.outputStream)
      ijentsRegistry.ijentsInternal[ijentId] = result
      return result
    }
  }
}

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

interface IjentChildProcess {
  val pid: Int
  val stdin: SendChannel<ByteArray>
  val stdout: ReceiveChannel<ByteArray>
  val stderr: ReceiveChannel<ByteArray>
  val exitCode: Deferred<Int>

  suspend fun sendSignal(signal: Int)  // TODO Use a separate class for signals.
}