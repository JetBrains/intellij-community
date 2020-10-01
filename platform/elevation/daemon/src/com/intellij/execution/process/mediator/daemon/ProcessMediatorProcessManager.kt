// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.ByteString
import com.intellij.execution.process.mediator.daemon.FdConstants.STDERR
import com.intellij.execution.process.mediator.daemon.FdConstants.STDIN
import com.intellij.execution.process.mediator.daemon.FdConstants.STDOUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

typealias Pid = Long

internal class ProcessMediatorProcessManager {
  private val processMap = ConcurrentHashMap<Pid, Process>()

  suspend fun createProcess(command: List<String>, workingDir: File, environVars: Map<String, String>,
                            inFile: File?, outFile: File?, errFile: File?): Pid {
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

    return registerProcess(process)
  }

  fun destroyProcess(pid: Pid, force: Boolean) {
    val process = getProcess(pid)
    if (force) {
      process.destroyForcibly()
    }
    else {
      process.destroy()
    }
  }

  suspend fun awaitTermination(pid: Pid): Int {
    val process = getProcess(pid)

    withContext(Dispatchers.IO) {
      process.onExit().await()
    }

    return process.exitValue()
  }

  fun readStream(pid: Pid, fd: Int): Flow<ByteString> {
    val process = getProcess(pid)
    val inputStream = when (fd) {
      STDOUT -> process.inputStream
      STDERR -> process.errorStream
      else -> throw FileNotFoundException("Unknown process output FD $fd for PID $pid")
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

  suspend fun writeStream(pid: Pid, fd: Int, chunkFlow: Flow<ByteString>) {
    val process = getProcess(pid)
    val outputStream = when (fd) {
      STDIN -> process.outputStream
      else -> throw FileNotFoundException("Unknown process input FD $fd for PID $pid")
    }
    @Suppress("BlockingMethodInNonBlockingContext")
    withContext(Dispatchers.IO) {
      outputStream.use { outputStream ->
        chunkFlow.collect { chunk ->
          val buffer = chunk.toByteArray()
          outputStream.write(buffer)
          outputStream.flush()
        }
      }
    }
  }

  fun release(pid: Pid) {
    unregisterProcess(pid).destroyForcibly()
  }

  private fun registerProcess(process: Process): Pid {
    val pid = process.pid()
    processMap.putIfAbsent(pid, process).also { previous ->
      check(previous == null) { "Duplicate PID $pid" }
    }
    return pid
  }

  private fun getProcess(pid: Pid): Process {
    val process = processMap[pid]
    return requireNotNull(process) { "Unknown PID $pid" }
  }

  private fun unregisterProcess(pid: Pid): Process {
    val process = processMap.remove(pid)
    return requireNotNull(process) { "Unknown PID $pid" }
  }
}

private object FdConstants {
  const val STDIN = 0
  const val STDOUT = 1
  const val STDERR = 2
}
