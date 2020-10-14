// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.rpc.DaemonGrpcKt
import com.intellij.execution.process.mediator.rpc.DaemonHello
import io.grpc.Server
import io.grpc.ServerBuilder
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.system.exitProcess

open class ProcessMediatorServerDaemon private constructor(private val server: Server) : ProcessMediatorDaemon {
  val port get() = server.port

  init {
    server.start()
    System.err.println("Started server on port $port")
    Runtime.getRuntime().addShutdownHook(
      Thread {
        System.err.println("*** shutting down gRPC server since JVM is shutting down")
        this@ProcessMediatorServerDaemon.stop()
        System.err.println("*** server shut down")
      }
    )
  }

  constructor(builder: ServerBuilder<*>) : this(buildServer(builder))

  override fun stop() {
    server.shutdown()
  }

  override fun blockUntilShutdown() {
    server.awaitTermination()
  }

  companion object {
    private fun buildServer(builder: ServerBuilder<*>): Server {
      return builder
        .addService(ProcessManagerServerService.createServiceDefinition())
        .addService(DaemonService)
        .build()
    }
  }
}

object DaemonService: DaemonGrpcKt.DaemonCoroutineImplBase() {
  override suspend fun shutdown(request: Empty): Empty {
    // TODO think about
    // - should we destroy running processes
    // - how to stop server
    return Empty.getDefaultInstance()
  }
}

private fun die(message: String): Nothing {
  val programName = "ProcessMediatorDaemonMain"
  System.err.println("Usage: $programName <host> <port>")
  System.err.println(message)
  exitProcess(1)
}

fun main(args: Array<String>) {
  if (args.size != 2) {
    die("Expected exactly two arguments")
  }

  val daemon = ProcessMediatorServerDaemon(ServerBuilder.forPort(0))
  val daemonHello = DaemonHello.newBuilder()
    .setPort(daemon.port)
    .build()

  when (args[0]) {
    "--hello-port" -> {
      val port = args[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: die("Invalid port: '${args[1]}'")
      Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
        socket.getOutputStream().use { stream ->
          daemonHello.writeDelimitedTo(stream)
        }
      }
    }
    "--hello-file" -> {
      val helloFilePath = Path.of(args[1])
      Files.newOutputStream(helloFilePath, StandardOpenOption.WRITE).use { stream ->
        daemonHello.writeDelimitedTo(stream)
      }
    }
  }

  daemon.blockUntilShutdown()
}