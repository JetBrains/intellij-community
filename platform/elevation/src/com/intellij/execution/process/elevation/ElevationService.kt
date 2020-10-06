// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.mediator.MediatedProcess
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.daemon.createInProcessServerForTesting
import com.intellij.execution.process.mediator.startLocalProcessMediatorClientForTesting
import kotlinx.coroutines.CoroutineScope
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.stream.Collectors
import kotlin.coroutines.EmptyCoroutineContext

private val SCOPE = CoroutineScope(EmptyCoroutineContext)

fun main() {
  //org.apache.log4j.BasicConfigurator.configure()
  val commandLine = GeneralCommandLine("/bin/cat")

  val processMediatorServer = ProcessMediatorDaemon(createInProcessServerForTesting())
  processMediatorServer.start()

  startLocalProcessMediatorClientForTesting(SCOPE).use { processMediatorClient ->
    val processBuilder = commandLine.toProcessBuilder()
      .redirectInput(File("community/platform/elevation/src/com/intellij/execution/process/mediator/util/coroutineUtil.kt"))
      .redirectOutput(File("community/platform/elevation/src/com/intellij/execution/process/mediator/util/coroutineUtil.kt.out"))
      .redirectError(File("community/platform/elevation/src/com/intellij/execution/process/mediator/util/coroutineUtil.kt.err"))

    val process = MediatedProcess.create(processMediatorClient, processBuilder)
    println("pid: ${process.pid()}")
    OutputStreamWriter(process.outputStream).use {
      it.write("Hello ")
      it.flush()
      Thread.sleep(1000)
      it.write("World\n")
      it.flush()
    }
    process.destroy()
    val output = BufferedReader(InputStreamReader(process.inputStream))
      .lines().collect(Collectors.joining("\n"));
    println("output: ${output}")
    println("waitFor: ${process.waitFor()}")
    println("exitValue: ${process.exitValue()}")
  }


  processMediatorServer.stop()
  processMediatorServer.blockUntilShutdown()
}