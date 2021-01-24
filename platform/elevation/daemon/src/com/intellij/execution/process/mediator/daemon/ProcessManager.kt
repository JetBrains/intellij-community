// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.ByteString
import com.intellij.execution.process.mediator.daemon.FdConstants.STDERR
import com.intellij.execution.process.mediator.daemon.FdConstants.STDIN
import com.intellij.execution.process.mediator.daemon.FdConstants.STDOUT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

typealias Pid = Long

internal class ProcessManager(coroutineScope: CoroutineScope) : Closeable {
  private val handleMap = ConcurrentHashMap<Pid, Handle>()
  private val job = Job(coroutineScope.coroutineContext[Job])

  suspend fun createProcess(command: List<String>, workingDir: File, environVars: Map<String, String>,
                            inFile: File?, outFile: File?, errFile: File?): Pid {
    // The ref job acts like a reference in ref-counting collectors preventing this.job from completion
    // (a parent job does not complete until all its children complete).
    val refJob = Job(job)
    refJob.ensureActive()
    try {
      val processBuilder = ProcessBuilder().apply {
        command(command)
        directory(workingDir)
        environment().run {
          clear()
          putAll(environVars)
        }
        inFile?.let { redirectInput(it) }
        outFile?.let { redirectOutput(it) }
        errFile?.let { redirectError(it) }
      }

      val process = withContext(Dispatchers.IO) {
        @Suppress("BlockingMethodInNonBlockingContext")
        processBuilder.start()
      }

      val handle = Handle(process, refJob)
      return registerHandle(handle)
    }
    catch (e: Throwable) {
      refJob.cancel("Failed to create process", e)
      throw e
    }
  }

  fun destroyProcess(pid: Pid, force: Boolean, destroyGroup: Boolean) {
    val handle = getHandle(pid)
    val process = handle.process
    val processHandle = process.toHandle()
    if (destroyGroup) {
      processHandle.doDestroyRecursively(force)
    }
    else {
      processHandle.doDestroy(force)
    }
  }

  private fun ProcessHandle.doDestroyRecursively(force: Boolean) {
    for (child in children()) {
      child.doDestroyRecursively(force)
    }
    doDestroy(force)
  }

  private fun ProcessHandle.doDestroy(force: Boolean) {
    if (force) {
      destroyForcibly()
    }
    else {
      destroy()
    }
  }

  suspend fun awaitTermination(pid: Pid): Int {
    val handle = getHandle(pid)
    val process = handle.process

    handle.completion.await()

    return process.exitValue()
  }

  fun readStream(pid: Pid, fd: Int): Flow<ByteString> {
    val handle = getHandle(pid)
    val process = handle.process
    val inputStream = when (fd) {
      STDOUT -> process.inputStream
      STDERR -> process.errorStream
      else -> throw IllegalArgumentException("Unknown process output FD $fd for PID $pid")
    }
    val buffer = ByteArray(8192)
    @Suppress("BlockingMethodInNonBlockingContext", "EXPERIMENTAL_API_USAGE")  // note the .flowOn(Dispatchers.IO) below
    return flow<ByteString> {
      while (true) {
        val n = inputStream.read(buffer)
        if (n < 0) break
        val chunk = ByteString.copyFrom(buffer, 0, n)
        emit(chunk)
      }
    }.onCompletion {
      inputStream.close()
    }.flowOn(Dispatchers.IO)
  }

  suspend fun writeStream(pid: Pid, fd: Int, chunkFlow: Flow<ByteString>, ackChannel: SendChannel<Unit>?) {
    val handle = getHandle(pid)
    val process = handle.process
    val outputStream = when (fd) {
      STDIN -> process.outputStream
      else -> throw IllegalArgumentException("Unknown process input FD $fd for PID $pid")
    }
    @Suppress("BlockingMethodInNonBlockingContext")
    withContext(Dispatchers.IO) {
      outputStream.use { outputStream ->
        handle.cancelJobOnCompletion(currentCoroutineContext()[Job]!!)
        @Suppress("EXPERIMENTAL_API_USAGE")
        chunkFlow.onCompletion {
          ackChannel?.close(it)
        }.collect { chunk ->
          val buffer = chunk.toByteArray()
          outputStream.write(buffer)
          outputStream.flush()
          ackChannel?.send(Unit)
        }
      }
    }
  }

  fun release(pid: Pid) {
    unregisterHandle(pid).release()
  }

  private fun registerHandle(handle: Handle): Pid {
    val pid = handle.pid
    handleMap.putIfAbsent(pid, handle).also { previous ->
      check(previous == null) { "Duplicate PID $pid" }
    }
    return pid
  }

  private fun getHandle(pid: Pid): Handle {
    val handle = handleMap[pid]
    return requireNotNull(handle) { "Unknown PID $pid" }
  }

  private fun unregisterHandle(pid: Pid): Handle {
    val handle = handleMap.remove(pid)
    return requireNotNull(handle) { "Unknown PID $pid" }
  }

  override fun close() {
    job.cancel("closed")
    while (true) {
      val pid = handleMap.keys.firstOrNull() ?: break
      handleMap.remove(pid)?.release()
    }
  }

  private data class Handle(
    val process: Process,
    private val refJob: CompletableJob,
  ) {
    val completion = CompletableDeferred<Int>(refJob)
    val pid get() = process.pid()

    init {
      process.onExit().whenComplete { p, _ ->
        completion.complete(p.exitValue())
      }
      refJob.complete()  // doesn't really complete until its child completes
    }

    fun cancelJobOnCompletion(job: Job) {
      completion.invokeOnCompletion { cause ->
        job.cancel(cause as? CancellationException ?: CancellationException("Process exited", cause))
      }.also { disposableHandle ->
        job.invokeOnCompletion { disposableHandle.dispose() }
      }
    }

    fun release() {
      refJob.cancel("process released")
      process.destroy()  // TODO should we really destroy it?
    }
  }
}

private object FdConstants {
  const val STDIN = 0
  const val STDOUT = 1
  const val STDERR = 2
}
