// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.google.protobuf.ByteString
import com.intellij.execution.process.mediator.util.blockingGet
import com.intellij.execution.process.mediator.util.childSupervisorScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.*
import java.lang.ref.Cleaner

private val CLEANER = Cleaner.create()

internal class MediatedProcess private constructor(private val handle: MediatedProcessHandle) : Process() {
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
      return MediatedProcess(handle).also { process ->
        val cleanable = CLEANER.register(process, handle::close)
        processMediatorClient.registerCleanup(cleanable::clean)
      }
    }
  }

  private val stdin: OutputStream = if (handle.inFile != null) OutputStream.nullOutputStream() else createOutputStream(0)
  private val stdout: InputStream = createInputStream(1)
  private val stderr: InputStream = createInputStream(2)

  private val termination: Deferred<Int> = handle.async {
    handle.rpc {
      processMediatorClient.awaitTermination(pid.await())
    }
  }

  override fun pid(): Long = handle.pid.blockingGet()

  override fun getOutputStream(): OutputStream = stdin
  override fun getInputStream(): InputStream = stdout
  override fun getErrorStream(): InputStream = stderr

  private fun createOutputStream(@Suppress("SameParameterValue") fd: Int): OutputStream {
    @Suppress("EXPERIMENTAL_API_USAGE")
    val channel = handle.actor<ByteString>(capacity = Channel.BUFFERED) {
      handle.rpc {
        processMediatorClient.writeStream(pid.await(), fd, channel.consumeAsFlow())
      }
    }
    val stream = ChannelOutputStream(channel)
    return BufferedOutputStream(stream)
  }

  private fun createInputStream(fd: Int): InputStream {
    @Suppress("EXPERIMENTAL_API_USAGE")
    val channel = handle.produce(capacity = Channel.BUFFERED) {
      handle.rpc {
        processMediatorClient.readStream(pid.await(), fd).collect(channel::send)
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
    handle.launch {
      handle.rpc {
        processMediatorClient.destroyProcess(pid.await(), force)
      }
    }
  }
}

/**
 * All remote calls are performed using the provided [ProcessMediatorClient],
 * and the whole process lifecycle is contained within its coroutine scope.
 */
private class MediatedProcessHandle(
  val processMediatorClient: ProcessMediatorClient,
  command: List<String>,
  workingDir: File,
  environVars: Map<String, String>,
  val inFile: File?,
  outFile: File?,
  errFile: File?,
) : CoroutineScope by processMediatorClient.childSupervisorScope(),
    AutoCloseable {

  val pid: Deferred<Long> = async {
    processMediatorClient.createProcess(command, workingDir, environVars, inFile, outFile, errFile)
  }

  private val cleanupJob = launch(start = CoroutineStart.LAZY) {
    // must be called exactly once;
    // once invoked, the pid is no more valid, and the process must be assumed reaped
    processMediatorClient.release(pid.await())
  }

  /** Controls all operations except CreateProcess() and Release(). */
  private val rpcAwaitingJob = SupervisorJob(coroutineContext[Job])

  suspend fun <R> rpc(block: suspend MediatedProcessHandle.() -> R): R {
    // This might require a bit of explanation.
    //
    // We want to ensure the Release() rpc is not started until any other RPC finishes.
    // This is achieved by making any RPC from within the outer withContext() coroutine
    // (a child of 'rpcAwaitingJob', which means the latter can't complete until all its children do).
    // But at the same time it is desirable to ensure the original caller coroutine still
    // controls the cancellation of the RPC, that is why the original job is restored as a parent using
    // the inner withContext().
    //
    // In fact, the 'rpcAwaitingJob' never gets cancelled explicitly at all,
    // only through the parent scope of MediatedProcessHandle, or finishes using the complete() call in release().
    // It is only used for this single purpose - to ensure strict ordering relation between any RPC call and
    // the final Release() call.
    //
    // In other words, if there was an RW lock for coroutines, this whole thing would be replaced by
    // trying to acquire a read lock in this method, and acquiring a write lock in release().
    val originalJob = currentCoroutineContext()[Job]!!
    return withContext(rpcAwaitingJob) {
      withContext(coroutineContext + originalJob) {
        block()
      }
    }
  }

  /** Once this is invoked, calling any other methods will throw [CancellationException]. */
  suspend fun release() {
    try {
      // let ongoing operations finish gracefully, but don't accept new calls
      rpcAwaitingJob.complete()
      rpcAwaitingJob.join()
    }
    finally {
      cleanupJob.join()
    }
  }

  override fun close() {
    runBlocking {
      release()
    }
  }
}
