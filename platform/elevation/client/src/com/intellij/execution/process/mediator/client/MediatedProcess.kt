// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.client

import com.google.protobuf.ByteString
import com.intellij.execution.process.SelfKiller
import com.intellij.execution.process.mediator.daemon.QuotaExceededException
import com.intellij.execution.process.mediator.util.ChannelInputStream
import com.intellij.execution.process.mediator.util.ChannelOutputStream
import com.intellij.execution.process.mediator.util.blockingGet
import com.intellij.execution.process.mediator.util.childSupervisorJob
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import java.io.*
import java.lang.ref.Cleaner
import kotlin.coroutines.coroutineContext

private val CLEANER = Cleaner.create()

open class MediatedProcess private constructor(private val handle: MediatedProcessHandle,
                                               pipeStdin: Boolean,
                                               pipeStdout: Boolean,
                                               pipeStderr: Boolean) : Process(), SelfKiller {

  init {
    @Suppress("LeakingThis")
    CLEANER.register(this, handle::releaseAsync)
  }

  companion object {
    @Throws(IOException::class,
            QuotaExceededException::class,
            CancellationException::class)
    fun create(processMediatorClient: ProcessMediatorClient,
               processBuilder: ProcessBuilder) = MediatedProcess(processMediatorClient,
                                                                 processBuilder)
  }

  constructor(
    processMediatorClient: ProcessMediatorClient,
    processBuilder: ProcessBuilder
  ) : this(
    processMediatorClient,
    processBuilder.command(),
    processBuilder.directory() ?: File(".").normalize(),  // defaults to current working directory
    processBuilder.environment(),
    processBuilder.redirectInput().file(),
    processBuilder.redirectOutput().file(),
    processBuilder.redirectError().file(),
  )

  private constructor(
    processMediatorClient: ProcessMediatorClient,
    command: List<String>,
    workingDir: File,
    environVars: Map<String, String>,
    inFile: File?,
    outFile: File?,
    errFile: File?,
  ) : this(
    handle = MediatedProcessHandle(processMediatorClient) {
      createProcess(command, workingDir, environVars, inFile, outFile, errFile)
    },
    pipeStdin = (inFile == null),
    pipeStdout = (outFile == null),
    pipeStderr = (errFile == null),
  )

  // if anything goes wrong during process creation, this will fail with the corresponding exception
  private val pid = handle.pid.blockingGet()

  private val stdin: OutputStream = if (pipeStdin) createOutputStream(0) else NullOutputStream
  private val stdout: InputStream = if (pipeStdout) createInputStream(1) else NullInputStream
  private val stderr: InputStream = if (pipeStderr) createInputStream(2) else NullInputStream

  private val termination: Deferred<Int> = handle.rpcScope.async {
    handle.rpc {
      awaitTermination(pid)
    }
  }

  override fun pid(): Long = pid

  override fun getOutputStream(): OutputStream = stdin
  override fun getInputStream(): InputStream = stdout
  override fun getErrorStream(): InputStream = stderr

  @Suppress("EXPERIMENTAL_API_USAGE")
  private fun createOutputStream(@Suppress("SameParameterValue") fd: Int): OutputStream {
    val ackFlow = MutableStateFlow<Long?>(0L)

    val channel = handle.rpcScope.actor<ByteString>(capacity = Channel.BUFFERED) {
      handle.rpc {
        try {
          // NOTE: Must never consume the channel associated with the actor. In fact, the channel IS the actor coroutine,
          //       and cancelling it makes the coroutine die in a horrible way leaving the remote call in a broken state.
          writeStream(pid, fd, channel.receiveAsFlow())
            .onCompletion { ackFlow.value = null }
            .fold(0L) { l, _ ->
              (l + 1).also {
                ackFlow.value = it
              }
            }
        }
        catch (e: IOException) {
          channel.cancel(CancellationException(e.message, e))
        }
      }
    }
    val stream = ChannelOutputStream(channel, ackFlow)
    return BufferedOutputStream(stream)
  }

  @Suppress("EXPERIMENTAL_API_USAGE")
  private fun createInputStream(fd: Int): InputStream {
    val channel = handle.rpcScope.produce<ByteString>(capacity = Channel.BUFFERED) {
      handle.rpc {
        try {
          readStream(pid, fd).collect(channel::send)
        }
        catch (e: IOException) {
          channel.close(e)
        }
      }
    }
    val stream = ChannelInputStream(channel)
    return BufferedInputStream(stream)
  }

  override fun waitFor(): Int = termination.blockingGet()

  override fun exitValue(): Int {
    return try {
      @Suppress("EXPERIMENTAL_API_USAGE")
      termination.getCompleted()
    }
    catch (e: IllegalStateException) {
      throw IllegalThreadStateException(e.message)
    }
  }

  override fun destroy() {
    destroy(false)
  }

  override fun destroyForcibly(): Process {
    destroy(true)
    return this
  }

  fun destroy(force: Boolean, destroyGroup: Boolean = false) {
    handle.rpcScope.launch {
      handle.rpc {
        destroyProcess(pid, force, destroyGroup)
      }
    }
  }

  private object NullInputStream : InputStream() {
    override fun read(): Int = -1
    override fun available(): Int = 0
  }

  private object NullOutputStream : OutputStream() {
    override fun write(b: Int) = throw IOException("Stream closed")
  }
}

/**
 * All remote calls are performed using the provided [ProcessMediatorClient],
 * and the whole process lifecycle is contained within the coroutine scope of the client.
 * Normal remote calls (those besides process creation and release) are performed within the scope of the handle object.
 */
private class MediatedProcessHandle(
  private val client: ProcessMediatorClient,
  createProcess: suspend ProcessMediatorClient.() -> Long,
) {

  private val parentScope: CoroutineScope = client.coroutineScope

  /** Controls all operations except CreateProcess() and Release(). */
  private val rpcJob: CompletableJob = parentScope.childSupervisorJob()
  val rpcScope: CoroutineScope = parentScope + rpcJob

  private val releaseJob = parentScope.launch(start = CoroutineStart.LAZY) {
    try {
      rpcJob.cancelAndJoin()
    }
    finally {
      // must be called exactly once;
      // once invoked, the pid is no more valid, and the process must be assumed reaped
      client.release(pid.await())
    }
  }.apply {
    rpcJob.invokeOnCompletion { start() }
  }

  val pid: Deferred<Long> = parentScope.async {
    try {
      client.createProcess()
    }
    catch (e: Throwable) {
      releaseJob.cancel("Failed to create process")
      rpcJob.cancel("Failed to create process")
      throw e
    }
  }

  suspend fun <R> rpc(block: suspend ProcessMediatorClient.() -> R): R {
    rpcScope.ensureActive()
    coroutineContext.ensureActive()
    // Perform the call in the scope of this handle, so that it is dispatched in the same way
    // as CreateProcess() and Release(). This overrides the parent so that we can await for
    // the call to complete before Release, but we ensure the caller is still able to cancel it.
    val deferred = rpcScope.async {
      client.block()
    }
    return try {
      deferred.await()
    }
    catch (e: CancellationException) {
      deferred.cancel(e)
      throw e
    }
  }

  fun releaseAsync() {
    // let ongoing operations finish gracefully,
    // and once all of them finish don't accept new calls
    rpcJob.complete()
  }
}
