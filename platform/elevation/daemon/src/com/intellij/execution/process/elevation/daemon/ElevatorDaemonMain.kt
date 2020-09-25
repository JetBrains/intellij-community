// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.daemon

import io.grpc.Server
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import java.net.InetSocketAddress
import kotlin.system.exitProcess

class ElevatorServer private constructor(private val server: Server) {
  constructor(host: String, port: Int) : this(createServer(host, port)) {
    println("Created server on $host:$port")
  }

  fun start() {
    server.start()
    Runtime.getRuntime().addShutdownHook(
      Thread {
        println("*** shutting down gRPC server since JVM is shutting down")
        this@ElevatorServer.stop()
        println("*** server shut down")
      }
    )
  }

  fun stop() {
    server.shutdown()
  }

  fun blockUntilShutdown() {
    server.awaitTermination()
  }

  companion object {
    fun createLocalElevatorServerForTesting(): ElevatorServer {
      val server = InProcessServerBuilder.forName("testing")
        .directExecutor()
        .addService(ElevatorServerService.createServiceDefinition())
        .build()
      return ElevatorServer(server)
    }
  }
}

private fun createServer(host: String, port: Int): Server {
  return NettyServerBuilder
    .forAddress(InetSocketAddress(host, port))
    .addService(ElevatorServerService.createServiceDefinition())
    .build()
}

private fun die(message: String): Nothing {
  val programName = "ElevatorDaemonMain"
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

  val server = ElevatorServer(host, port)
  server.start()
  server.blockUntilShutdown()
}