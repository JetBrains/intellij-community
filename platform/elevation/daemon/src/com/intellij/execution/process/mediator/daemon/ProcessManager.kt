// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.ByteString
import com.intellij.execution.process.mediator.daemon.FdConstants.STDERR
import com.intellij.execution.process.mediator.daemon.FdConstants.STDIN
import com.intellij.execution.process.mediator.daemon.FdConstants.STDOUT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

typealias Pid = Long

internal class ProcessManager : Closeable {
  private val handleIdCounter = AtomicLong()
  private val handleMap = ConcurrentHashMap<Pid, Handle>()

  fun openHandle(coroutineScope: CoroutineScope): Handle {
    val handleId = handleIdCounter.incrementAndGet()
    return Handle(handleId, coroutineScope).also { handle ->
      handleMap[handleId] = handle
      handle.lifetimeJob.invokeOnCompletion {
        handleMap.remove(handleId)  // may not be there when called from ProcessManager.close()
      }
    }
  }

  suspend fun createProcess(handleId: Long,
                            command: List<String>, workingDir: File, environVars: Map<String, String>,
                            inFile: File?, outFile: File?, errFile: File?): Pid {
    val handle = getHandle(handleId)

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
    val process = handle.startProcess(processBuilder)

    return process.pid()
  }

  fun destroyProcess(handleId: Long, force: Boolean, destroyGroup: Boolean) {
    val handle = getHandle(handleId)
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

  suspend fun awaitTermination(handleId: Long): Int {
    val handle = getHandle(handleId)
    val process = handle.process

    return process.onExit().await().exitValue()
  }

  fun readStream(handleId: Long, fd: Int): Flow<ByteString> {
    val handle = getHandle(handleId)
    val process = handle.process
    val inputStream = when (fd) {
      STDOUT -> process.inputStream
      STDERR -> process.errorStream
      else -> throw IllegalArgumentException("Unknown process output FD $fd for PID $handleId")
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

  suspend fun writeStream(handleId: Long, fd: Int, chunkFlow: Flow<ByteString>, ackChannel: SendChannel<Unit>?) {
    val handle = getHandle(handleId)
    val process = handle.process
    val outputStream = when (fd) {
      STDIN -> process.outputStream
      else -> throw IllegalArgumentException("Unknown process input FD $fd for PID $handleId")
    }
    @Suppress("BlockingMethodInNonBlockingContext")
    withContext(Dispatchers.IO) {
      outputStream.use { outputStream ->
        with(currentCoroutineContext()) {
          process.onExit().whenComplete { _, _ ->
            job.cancel("Process exited")
          }
        }
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

  private fun getHandle(handleId: Long): Handle {
    val handle = handleMap[handleId]
    return requireNotNull(handle) { "Unknown handle ID $handleId" }
  }

  override fun close() {
    while (true) {
      val handleId = handleMap.keys.firstOrNull() ?: break
      val handle = handleMap.remove(handleId) ?: continue
      handle.lifetimeJob.cancel("closed")
    }
  }

  class Handle(val handleId: Long, coroutineScope: CoroutineScope) {
    val lifetimeJob: Job = Job(coroutineScope.coroutineContext.job).also {
      it.ensureActive()
    }

    val process: Process
      get() = checkNotNull(_process) { "Process has not been created yet" }

    @Volatile
    private var _process: Process? = null

    suspend fun startProcess(processBuilder: ProcessBuilder): Process {
      check(_process == null) { "Process has already been initialized" }
      withContext(Dispatchers.IO) {
        synchronized(this) {
          check(_process == null) { "Process has already been initialized" }
          lifetimeJob.ensureActive()
          @Suppress("BlockingMethodInNonBlockingContext")
          _process = processBuilder.start()
        }
      }
      return process
    }
  }
}

private object FdConstants {
  const val STDIN = 0
  const val STDOUT = 1
  const val STDERR = 2
}
