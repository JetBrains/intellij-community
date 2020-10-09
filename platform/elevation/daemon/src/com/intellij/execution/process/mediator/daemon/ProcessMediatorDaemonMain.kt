// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import io.grpc.Server
import io.grpc.ServerBuilder
import java.net.InetAddress
import java.net.Socket
import kotlin.system.exitProcess

open class ProcessMediatorServerDaemon private constructor(private val server: Server) : ProcessMediatorDaemon {
  val port get() = server.port

  init {
    server.start()
    println("Started server on port $port")
    Runtime.getRuntime().addShutdownHook(
      Thread {
        println("*** shutting down gRPC server since JVM is shutting down")
        this@ProcessMediatorServerDaemon.stop()
        println("*** server shut down")
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
        .addService(ProcessMediatorServerService.createServiceDefinition())
        .build()
    }
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
  val host = args[0]
  val port = args[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: die("Invalid port: '${args[1]}'")

  val daemon = ProcessMediatorServerDaemon(ServerBuilder.forPort(0))

  Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
    socket.getOutputStream().writer(Charsets.UTF_8).use { writer ->
      writer.write(daemon.port.toString())
      writer.write("\n")
      writer.flush()
    }
  }

  daemon.blockUntilShutdown()
}