// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.rpc.DaemonGrpcKt
import com.intellij.execution.process.mediator.rpc.DaemonHello
import io.grpc.Server
import io.grpc.ServerBuilder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit


open class ProcessMediatorServerDaemon(builder: ServerBuilder<*>,
                                       credentials: DaemonClientCredentials) : ProcessMediatorDaemon {
  private val processManager = ProcessManager()
  private val server: Server

  val port get() = server.port

  init {
    this.server = builder
      .intercept(CredentialsAuthServerInterceptor(credentials))
      .addService(ProcessManagerServerService.createServiceDefinition(processManager))
      .addService(DaemonService())
      .build()
      .start()
      .also {
        System.err.println("Started server on port ${it.port}")
      }
  }

  override fun stop() {
    server.shutdown()
    System.err.println("server shut down")
  }

  override fun blockUntilShutdown() {
    server.awaitTermination()
  }

  inner class DaemonService : DaemonGrpcKt.DaemonCoroutineImplBase() {
    override suspend fun shutdown(request: Empty): Empty {
      processManager.use {  // to close it
        stop()
      }
      return Empty.getDefaultInstance()
    }
  }
}

private fun createDaemonProcessCommandLine(vararg args: String): ProcessBuilder {
  return ProcessBuilder(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                        *ProcessMediatorDaemonRuntimeClasspath.getProperties().map { (k, v) -> "-D$k=$v" }.toTypedArray(),
                        "-cp", System.getProperty("java.class.path"),
                        ProcessMediatorDaemonRuntimeClasspath.getMainClass().name,
                        *args)
}

private fun openHelloWriter(helloOption: DaemonLaunchOptions.HelloOption?): DaemonHelloWriter? =
  when (helloOption) {
    null -> null
    DaemonLaunchOptions.HelloOption.Stdout -> DaemonHelloStdoutWriter
    is DaemonLaunchOptions.HelloOption.File -> DaemonHelloFileWriter(helloOption.path)
    is DaemonLaunchOptions.HelloOption.Port -> DaemonHelloSocketWriter(helloOption.port)
  }

private fun DaemonHelloWriter?.writeHello(daemonHello: DaemonHello) {
  this?.write(daemonHello::writeDelimitedTo) ?: println(daemonHello)
}


private fun trampoline(launchOptions: DaemonLaunchOptions) {
  openHelloWriter(launchOptions.helloOption).use { helloWriter ->
    val daemonOptions = launchOptions.copy(trampoline = false,
                                           helloOption = DaemonLaunchOptions.HelloOption.Stdout)
    val daemonProcess = createDaemonProcessCommandLine(*daemonOptions.asCmdlineArgs().toTypedArray())
      .redirectInput(ProcessBuilder.Redirect.DISCARD)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .start()

    try {
      val daemonHello = daemonProcess.inputStream.use(DaemonHello::parseDelimitedFrom)
                        ?: throw IOException("Unable to read daemon hello")

      helloWriter.writeHello(daemonHello)
    }
    catch (e: Throwable) {
      if (e is IOException) System.err.println("Unable to relay daemon hello: ${e.message}")
      daemonProcess.run {
        destroy()
        kotlin.runCatching { waitFor(10, TimeUnit.SECONDS) }
        destroyForcibly()
      }
      throw e
    }
  }
}

fun main(args: Array<String>) {
  val launchOptions = DaemonLaunchOptions.parseFromArgsOrDie("ProcessMediatorDaemonMain", args)

  if (launchOptions.trampoline) {
    return trampoline(launchOptions)
  }

  val daemon: ProcessMediatorServerDaemon

  openHelloWriter(launchOptions.helloOption).use { helloWriter ->
    val credentials = DaemonClientCredentials.generate()
    daemon = ProcessMediatorServerDaemon(ServerBuilder.forPort(0), credentials)
    try {
      val token = when (val publicKey = launchOptions.publicKeyOption?.publicKey) {
        null -> credentials.token
        else -> credentials.rsaEncrypt(publicKey)
      }
      val daemonHello = DaemonHello.newBuilder()
        .setPort(daemon.port)
        .setToken(token)
        .build()

      helloWriter.writeHello(daemonHello)
    }
    catch (e: Throwable) {
      if (e is IOException) System.err.println("Unable to write hello: ${e.message}")
      daemon.stop()
      throw e
    }
  }

  Runtime.getRuntime().addShutdownHook(
    Thread {
      System.err.println("Shutting down gRPC server since JVM is shutting down")
      daemon.stop()
    }
  )
  daemon.blockUntilShutdown()
}
