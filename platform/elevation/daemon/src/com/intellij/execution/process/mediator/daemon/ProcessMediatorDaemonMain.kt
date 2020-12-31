// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.rpc.DaemonGrpcKt
import com.intellij.execution.process.mediator.rpc.DaemonHello
import com.intellij.execution.process.mediator.util.parseArgs
import io.grpc.Server
import io.grpc.ServerBuilder
import java.io.IOException
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.system.exitProcess


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


private fun die(message: String): Nothing {
  val programName = "ProcessMediatorDaemonMain"
  System.err.println(message)
  System.err.println("Usage: $programName [ --hello-file=file|- | --hello-port=port ] [ --token-encrypt-rsa=public-key ]")
  exitProcess(1)
}

fun main(args: Array<String>) {
  var helloWriter: DaemonHelloWriter? = null
  var publicKey: PublicKey? = null

  for ((option, value) in parseArgs(args)) {
    if (value == null) die("Missing '$option' value")

    when (option) {
      "--hello-file" -> {
        // handling multiple hello writers is too complicated w.r.t. resource management
        if (helloWriter != null) System.err.println("Ignoring '$option'")
        else helloWriter = if (value == "-") DaemonHelloStdoutWriter else DaemonHelloFileWriter(Path.of(value))
      }

      "--hello-port" -> {
        @Suppress("EXPERIMENTAL_API_USAGE")
        val port = value.toUShortOrNull() ?: die("Invalid port specified: value")
        if (helloWriter != null) System.err.println("Ignoring '$option'")
        else helloWriter = DaemonHelloSocketWriter(port)
      }

      "--token-encrypt-rsa" -> {
        val bytes = Base64.getDecoder().decode(value)
        publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
      }

      null -> die("Unrecognized positional argument '$value'")
      else -> die("Unrecognized option '$option'")
    }
  }

  helloWriter.use {
    val credentials = DaemonClientCredentials.generate()
    val token = if (publicKey != null) credentials.rsaEncrypt(publicKey) else credentials.token

    val daemon = ProcessMediatorServerDaemon(ServerBuilder.forPort(0), credentials)
    val daemonHello = DaemonHello.newBuilder()
      .setPort(daemon.port)
      .setToken(token)
      .build()

    try {
      try {
        helloWriter?.write(daemonHello::writeDelimitedTo) ?: println(daemonHello)  // human-readable
      }
      catch (e: IOException) {
        die("Unable to write hello: ${e.message}")
      }
    }
    catch (e: Throwable) {
      daemon.stop()
      throw e
    }

    Runtime.getRuntime().addShutdownHook(
      Thread {
        System.err.println("Shutting down gRPC server since JVM is shutting down")
        daemon.stop()
      }
    )
    daemon.blockUntilShutdown()
  }
}
