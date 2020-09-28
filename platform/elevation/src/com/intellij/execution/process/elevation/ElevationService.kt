// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.elevation.daemon.ElevatorDaemonRuntimeClasspath
import com.intellij.execution.process.elevation.daemon.ElevatorServer
import com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest
import com.intellij.execution.process.elevation.rpc.CommandLine
import com.intellij.execution.process.elevation.rpc.CreateProcessRequest
import com.intellij.execution.process.elevation.rpc.ElevatorGrpcKt.ElevatorCoroutineStub
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.TimeUnit

private fun startElevatorDaemon(): ElevatorClient {
  val host = if (java.lang.Boolean.getBoolean("java.net.preferIPv6Addresses")) "::1" else "127.0.0.1"
  val port = 50051

  val daemonCommandLine = createElevatorDaemonCommandLine(host, port.toString())
  val daemonProcessHandler = OSProcessHandler.Silent(daemonCommandLine).apply {
    addProcessListener(object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        println("Daemon exited with code ${event.exitCode}")
      }

      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        println("Daemon [$outputType]: ${event.text}")
      }
    })
  }
  daemonProcessHandler.startNotify()

  val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
  return ElevatorClient(channel)
}

private fun startLocalElevatorClientForTesting(): ElevatorClient {
  val channel = InProcessChannelBuilder.forName("testing").directExecutor().build()
  return ElevatorClient(channel)
}

private fun createElevatorDaemonCommandLine(host: String, port: String): GeneralCommandLine {
  val elevatorClass = ElevatorDaemonRuntimeClasspath.getMainClass().name
  val classpathClasses = ElevatorDaemonRuntimeClasspath.getClasspathClasses()
  val classpath = classpathClasses.mapNotNullTo(LinkedHashSet()) { it.getResourcePath() }.joinToString(File.pathSeparator)
  val javaVmExecutablePath = SystemProperties.getJavaHome() + File.separator + "bin" + File.separator + "java"

  return GeneralCommandLine(javaVmExecutablePath)
    .withParameters("-cp", classpath)
    .withParameters(elevatorClass)
    .withParameters(host, port)
}

private fun Class<*>.getResourcePath(): String? {
  return FileUtil.toCanonicalPath(PathManager.getResourceRoot(this, "/" + name.replace('.', '/') + ".class"))
}

private class ElevatedProcess private constructor(
  coroutineScope: CoroutineScope,
  private val elevatorClient: ElevatorClient,
  private val pid: Long
) : Process(),
    CoroutineScope by coroutineScope {
  companion object {
    fun create(coroutineScope: CoroutineScope,
               elevatorClient: ElevatorClient,
               processBuilder: ProcessBuilder): ElevatedProcess {
      val pid = runBlocking(coroutineScope.coroutineContext) {
        elevatorClient.createProcess(processBuilder.command(),
                                     processBuilder.directory() ?: File(".").normalize(),  // defaults to current working directory
                                     processBuilder.environment())
      }
      return ElevatedProcess(coroutineScope, elevatorClient, pid)
    }
  }

  private val reaper: Deferred<Int> = async {
    // must be called exactly once;
    // once invoked, the pid is no more valid, and the process must be assumed reaped
    elevatorClient.awaitTermination(pid)
  }

  override fun pid(): Long {
    return pid
  }

  override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
  override fun getInputStream(): InputStream = InputStream.nullInputStream()
  override fun getErrorStream(): InputStream = InputStream.nullInputStream()

  override fun waitFor(): Int {
    return runBlocking {
      reaper.await()
    }
  }

  override fun exitValue(): Int {
    return try {
      @Suppress("EXPERIMENTAL_API_USAGE")
      reaper.getCompleted()
    }
    catch (e: IllegalStateException) {
      throw IllegalThreadStateException(e.message)
    }
  }

  override fun destroy() {
    TODO("Not yet implemented")
  }
}

private class ElevatorClient(private val channel: ManagedChannel) : Closeable {
  private val stub: ElevatorCoroutineStub = ElevatorCoroutineStub(channel)

  suspend fun createProcess(command: List<String>, workingDir: File, environVars: Map<String, String>): Long {
    val environVarList = environVars.map { (name, value) ->
      CommandLine.EnvironVar.newBuilder()
        .setName(name)
        .setValue(value)
        .build()
    }
    val commandLine = CommandLine.newBuilder()
      .addAllCommand(command)
      .setWorkingDir(workingDir.absolutePath)
      .addAllEnvironVars(environVarList)
      .build()
    val request = CreateProcessRequest.newBuilder().setCommandLine(commandLine).build()
    val response = stub.createProcess(request)
    return response.pid
  }

  suspend fun awaitTermination(pid: Long): Int {
    val request = AwaitTerminationRequest.newBuilder()
      .setPid(pid)
      .build()
    val reply = stub.awaitTermination(request)
    return reply.exitCode
  }

  override fun close() {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
  }
}

fun main() {
  //org.apache.log4j.BasicConfigurator.configure()
  val commandLine = GeneralCommandLine("/bin/echo", "hello")

  val elevatorServer = ElevatorServer.createLocalElevatorServerForTesting()
  elevatorServer.start()

  startLocalElevatorClientForTesting().use { elevatorClient ->
    val elevatedProcess = ElevatedProcess.create(GlobalScope,
                                                 elevatorClient,
                                                 commandLine.toProcessBuilder())
    println("pid: ${elevatedProcess.pid()}")
    println("waitFor: ${elevatedProcess.waitFor()}")
    println("exitValue: ${elevatedProcess.exitValue()}")
  }


  elevatorServer.stop()
  elevatorServer.blockUntilShutdown()
}