// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.google.protobuf.ByteString
import com.intellij.execution.process.mediator.util.blockingGet
import com.intellij.execution.process.mediator.util.childSupervisorJob
import com.intellij.execution.process.mediator.util.childSupervisorScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import java.io.*
import java.lang.ref.Cleaner

private val CLEANER = Cleaner.create()

class MediatedProcess private constructor(private val handle: MediatedProcessHandle,
                                          pipeStdin: Boolean,
                                          pipeStdout: Boolean,
                                          pipeStderr: Boolean) : Process(), CoroutineScope by handle {
  companion object {
    fun create(processMediatorClient: ProcessMediatorClient,
               processBuilder: ProcessBuilder): MediatedProcess {
      return create(processMediatorClient,
                    processBuilder.command(),
                    processBuilder.directory() ?: File(".").normalize(),  // defaults to current working directory
                    processBuilder.environment(),
                    processBuilder.redirectInput().file(),
                    processBuilder.redirectOutput().file(),
                    processBuilder.redirectError().file())
    }

    private fun create(
      processMediatorClient: ProcessMediatorClient,
      command: List<String>,
      workingDir: File,
      environVars: Map<String, String>,
      inFile: File?,
      outFile: File?,
      errFile: File?,
    ): MediatedProcess {
      val handle = MediatedProcessHandle(processMediatorClient, command, workingDir, environVars, inFile, outFile, errFile)
      return MediatedProcess(
        handle,
        pipeStdin = (inFile == null),
        pipeStdout = (outFile == null),
        pipeStderr = (errFile == null),
      ).also { process ->
        CLEANER.register(process, handle::releaseAsync)
      }
    }
  }

  // if anything goes wrong during process creation, this will fail with the corresponding exception
  private val pid = handle.pid.blockingGet()

  private val stdin: OutputStream = if (pipeStdin) createOutputStream(0) else NullOutputStream
  private val stdout: InputStream = if (pipeStdout) createInputStream(1) else NullInputStream
  private val stderr: InputStream = if (pipeStderr) createInputStream(2) else NullInputStream

  private val termination: Deferred<Int> = async {
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

    val channel = actor<ByteString>(capacity = Channel.BUFFERED) {
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
    val channel = produce<ByteString>(capacity = Channel.BUFFERED) {
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

  private fun destroy(force: Boolean) {
    launch {
      handle.rpc {
        destroyProcess(pid, force)
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
 * and the whole process lifecycle is contained within its coroutine scope.
 */
private class MediatedProcessHandle(
  private val client: ProcessMediatorClient,
  command: List<String>,
  workingDir: File,
  environVars: Map<String, String>,
  inFile: File?,
  outFile: File?,
  errFile: File?,
) : CoroutineScope by client.childSupervisorScope() {

  /** Controls all operations except CreateProcess() and Release(). */
  private val rpcJob = childSupervisorJob()

  private val releaseJob = launch(start = CoroutineStart.LAZY) {
    try {
      rpcJob.cancelAndJoin()
    }
    finally {
      // must be called exactly once;
      // once invoked, the pid is no more valid, and the process must be assumed reaped
      client.release(pid.await())
    }
  }

  val pid: Deferred<Long> = async {
    try {
      client.createProcess(command, workingDir, environVars, inFile, outFile, errFile)
    }
    catch (e: Throwable) {
      rpcJob.cancel("Failed to create process")
      releaseJob.cancel("Failed to create process")
      throw e
    }
  }

  suspend fun <R> rpc(block: suspend ProcessMediatorClient.() -> R): R {
    (this as CoroutineScope).ensureActive()
    currentCoroutineContext().ensureActive()
    // Perform the call in the scope of this handle, so that it is dispatched in the same way
    // as CreateProcess() and Release(). The parent is overridden so that we can await for
    // the call to complete before Release, but the caller is still able to cancel it.
    val deferred = (this as CoroutineScope).async(rpcJob) {
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

  /** Once this is invoked, attempting to make any RPC will throw [CancellationException]. */
  fun releaseAsync() {
    // let ongoing operations finish gracefully, but don't accept new calls
    rpcJob.complete()
    releaseJob.start()
  }
}
