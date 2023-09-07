// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.attachAsChildTo
import com.intellij.util.namedChildScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
   */
  @OverrideOnly
  suspend fun connect(
    id: Long,
    communicationCoroutineScope: CoroutineScope,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi

  companion object {
    private val LOG = logger<IjentSessionProvider>()

    private val counter = AtomicLong()

    /**
     * The session exits when one of the following happens:
     * * The job corresponding to [communicationCoroutineScope] is finished.
     * * [epCoroutineScope] is finished.
     * * [inputStream] is closed.
     */
    suspend fun connect(communicationCoroutineScope: CoroutineScope, process: Process): IjentApi {
      val provider = serviceAsync<IjentSessionProvider>()
      val id = counter.getAndIncrement()
      val label = "IJent #$id"
      val epCoroutineScope = provider.epCoroutineScope
      val childScope = communicationCoroutineScope
        .namedChildScope(label, supervisor = false)
        .apply { attachAsChildTo(epCoroutineScope) }
      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$label > watchdog")) {
        while (true) {
          if (process.waitFor(10, TimeUnit.MILLISECONDS)) {
            val exitValue = process.exitValue()
            LOG.debug { "$label exit code $exitValue" }
            check(exitValue == 0) { "Process has exited with code $exitValue" }
            cancel()
            break
          }
          delay(100)
        }
      }
      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$label > finalizer")) {
        try {
          awaitCancellation()
        }
        catch (err: Exception) {
          LOG.debug(err) { "$label is going to be terminated due to receiving an error" }
          throw err
        }
        finally {
          if (process.isAlive) {
            GlobalScope.launch(Dispatchers.IO + coroutineNameAppended("actual destruction")) {
              try {
                if (process.waitFor(5, TimeUnit.SECONDS)) {
                  LOG.debug { "$label exit code ${process.exitValue()}" }
                }
              }
              finally {
                if (process.isAlive) {
                  LOG.debug { "The process $label is still alive, it will be killed" }
                  process.destroy()
                }
              }
            }
            GlobalScope.launch(Dispatchers.IO) {
              LOG.debug { "Closing stdin of $label" }
              process.outputStream.close()
            }
          }
        }
      }

      val processScopeNamePrefix = childScope.coroutineContext[CoroutineName]?.let { "$it >" } ?: ""

      epCoroutineScope.launch(Dispatchers.IO) {
        withContext(coroutineNameAppended("$processScopeNamePrefix $label > logger")) {
          process.errorReader().use { errorReader ->
            for (line in errorReader.lineSequence()) {
              // TODO It works incorrectly with multiline log messages.
              when (line.splitToSequence(' ').drop(1).take(1).firstOrNull()) {
                "TRACE" -> LOG.trace { "$label log: $line" }
                "DEBUG" -> LOG.debug { "$label log: $line" }
                "INFO" -> LOG.info("$label log: $line")
                "WARN" -> LOG.warn("$label log: $line")
                "ERROR" -> LOG.error("$label log: $line")
                else -> LOG.trace { "$label log: $line" }
              }
              yield()
            }
          }
        }
      }
      return provider.connect(id, childScope, process.inputStream, process.outputStream)
    }
  }
}

interface IjentApi {
  suspend fun executeProcess(exe: String, vararg args: String, env: Map<String, String> = emptyMap()): ExecuteProcessResult

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
}

interface IjentChildProcess {
  val pid: Int
  val stdin: SendChannel<ByteArray>
  val stdout: ReceiveChannel<ByteArray>
  val stderr: ReceiveChannel<ByteArray>
  val exitCode: Deferred<Int>

  suspend fun sendSignal(signal: Int)  // TODO Use a separate class for signals.
}