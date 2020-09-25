// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.elevation.daemon.ElevatorDaemonRuntimeClasspath
import com.intellij.execution.process.elevation.rpc.CommandLine
import com.intellij.execution.process.elevation.rpc.ElevatorGrpcKt.ElevatorCoroutineStub
import com.intellij.execution.process.elevation.rpc.SpawnRequest
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.runBlocking
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
  private val elevatorClient: ElevatorClient,
  private val pid: Long
) : Process() {
  companion object {
    fun create(elevatorClient: ElevatorClient,
               processBuilder: ProcessBuilder): ElevatedProcess {
      val pid = runBlocking {
        elevatorClient.spawn(processBuilder.command(),
                             processBuilder.directory() ?: File("."),  // defaults to current working directory
                             processBuilder.environment())
      }
      return ElevatedProcess(elevatorClient, pid)
    }
  }

  override fun pid(): Long {
    return pid
  }

  override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
  override fun getInputStream(): InputStream = InputStream.nullInputStream()
  override fun getErrorStream(): InputStream = InputStream.nullInputStream()

  override fun waitFor(): Int {
    TODO("Not yet implemented")
  }

  override fun exitValue(): Int {
    TODO("Not yet implemented")
  }

  override fun destroy() {
    TODO("Not yet implemented")
  }
}

private class ElevatorClient(private val channel: ManagedChannel) : Closeable {
  private val stub: ElevatorCoroutineStub = ElevatorCoroutineStub(channel)

  suspend fun spawn(command: List<String>, workingDir: File, environVars: Map<String, String>): Long {
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
    val request = SpawnRequest.newBuilder().setCommandLine(commandLine).build()
    val response = stub.spawn(request)
    return response.pid
  }

  override fun close() {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
  }
}

fun main() {
  //org.apache.log4j.BasicConfigurator.configure()
  val commandLine = GeneralCommandLine("/bin/echo", "hello")
  startElevatorDaemon().use { elevatorClient ->
    val elevatedProcess = ElevatedProcess.create(elevatorClient,
                                                 commandLine.toProcessBuilder())
    println("pid: ${elevatedProcess.pid()}")
  }
}