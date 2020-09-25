// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.daemon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

typealias Pid = Long

internal class ElevatorProcessManager {
  private val processMap = mutableMapOf<Pid, Process>()
  private val mutex = Mutex()

  suspend fun createProcess(command: List<String>, workingDir: File, environVars: Map<String, String>): Pid {
    val processBuilder = ProcessBuilder().apply {
      command(command)
      directory(workingDir)
      environment().run {
        clear()
        putAll(environVars)
      }
    }

    val process = withContext(Dispatchers.IO) {
      @Suppress("BlockingMethodInNonBlockingContext")
      processBuilder.start()
    }

    return registerProcess(process)
  }

  private suspend fun registerProcess(process: Process): Pid {
    val pid = process.pid()
    mutex.withLock {
      processMap.putIfAbsent(pid, process).also { previous ->
        check(previous == null) { "Duplicate PID $pid" }
      }
    }
    return pid
  }
}