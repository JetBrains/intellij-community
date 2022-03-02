// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.intellij.execution.process.mediator.client

import com.google.protobuf.ByteString
import com.intellij.execution.process.SelfKiller
import com.intellij.execution.process.mediator.daemon.QuotaExceededException
import com.intellij.execution.process.mediator.util.ChannelInputStream
import com.intellij.execution.process.mediator.util.ChannelOutputStream
import com.intellij.execution.process.mediator.util.blockingGet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asCompletableFuture
import java.io.*
import java.lang.ref.Cleaner
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.coroutineContext

private val CLEANER = Cleaner.create()

class MediatedProcess private constructor(
  private val handle: MediatedProcessHandle,
  command: List<String>, workingDir: File, environVars: Map<String, String>,
  inFile: File?, outFile: File?, errFile: File?, redirectErrorStream: Boolean
) : Process(), SelfKiller {

  companion object {
    @Throws(IOException::class,
            QuotaExceededException::class,
            CancellationException::class)
    fun create(processMediatorClient: ProcessMediatorClient,
               processBuilder: ProcessBuilder): MediatedProcess {
      val workingDir = processBuilder.directory() ?: File(".").normalize()  // defaults to current working directory
      val inFile = processBuilder.redirectInput().file()
      val outFile = processBuilder.redirectOutput().file()
      val errFile = processBuilder.redirectError().file()

      val handle = MediatedProcessHandle.create(processMediatorClient)
      return try {
        MediatedProcess(handle,
                        processBuilder.command(), workingDir, processBuilder.environment(),
                        inFile, outFile, errFile, processBuilder.redirectErrorStream()).apply {
          val cleanable = CLEANER.register(this, handle::releaseAsync)
          onExit().thenRun { cleanable.clean() }
        }
      }
      catch (e: Throwable) {
        handle.rpcScope.cancel(e as? CancellationException ?: CancellationException("Failed to create process", e))
        throw e
      }
    }
  }

  private val pid = runBlocking {
    handle.rpc { handleId ->
      createProcess(handleId, command, workingDir, environVars, inFile, outFile, errFile, redirectErrorStream)
    }
  }

  private val stdin: OutputStream = if (inFile == null) createOutputStream(0) else NullOutputStream
  private val stdout: InputStream = if (outFile == null) createInputStream(1) else NullInputStream
  private val stderr: InputStream = if (errFile == null && !redirectErrorStream) createInputStream(2) else NullInputStream

  private val termination: Deferred<Int> = handle.rpcScope.async {
    handle.rpc { handleId ->
      awaitTermination(handleId)
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
      handle.rpc { handleId ->
        try {
          // NOTE: Must never consume the channel associated with the actor. In fact, the channel IS the actor coroutine,
          //       and cancelling it makes the coroutine die in a horrible way leaving the remote call in a broken state.
          writeStream(handleId, fd, channel.receiveAsFlow())
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
      handle.rpc { handleId ->
        try {
          readStream(handleId, fd).collect(channel::send)
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
  override fun onExit(): CompletableFuture<Process> = termination.asCompletableFuture().thenApply { this }

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
    // In case this is called after releasing the handle (due to process termination),
    // it just does nothing, without throwing any error.
    handle.rpcScope.launch {
      handle.rpc { handleId ->
        destroyProcess(handleId, force, destroyGroup)
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
private class MediatedProcessHandle private constructor(
  private val client: ProcessMediatorClient,
  private val handleId: Long,
  private val lifetimeChannel: ReceiveChannel<*>,
) {
  companion object {
    fun create(client: ProcessMediatorClient): MediatedProcessHandle {
      val lifetimeChannel = client.openHandle().produceIn(client.coroutineScope)
      val handleId = runBlocking {
        lifetimeChannel.receiveCatching().getOrElse {
          val ex = it ?: IOException("Failed to receive handleId")
          lifetimeChannel.cancel(ex as? CancellationException ?: CancellationException("Failed to initialize client-side handle", ex))
          throw ex
        }
      }
      return MediatedProcessHandle(client, handleId, lifetimeChannel)
    }
  }

  private val parentScope: CoroutineScope = client.coroutineScope

  private val lifetimeJob = parentScope.launch {
    try {
      lifetimeChannel.receive()
    }
    catch (e: ClosedReceiveChannelException) {
      throw CancellationException("closed", e)
    }
    error("unreachable")
  }
  private val rpcJob: CompletableJob = SupervisorJob(lifetimeJob).apply {
    invokeOnCompletion { e ->
      lifetimeChannel.cancel(e as? CancellationException ?: CancellationException("Complete", e))
    }
  }
  val rpcScope: CoroutineScope = parentScope + rpcJob

  suspend fun <R> rpc(block: suspend ProcessMediatorClient.(handleId: Long) -> R): R {
    rpcScope.ensureActive()
    coroutineContext.ensureActive()
    // Perform the call in the scope of this handle, so that it is dispatched in the same way as OpenHandle().
    // This overrides the parent so that we can await for the call to complete before closing the lifetimeChannel;
    // at the same time we ensure the caller is still able to cancel it.
    val deferred = rpcScope.async {
      client.block(handleId)
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
