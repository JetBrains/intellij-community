// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import java.net.InetSocketAddress
import kotlin.system.exitProcess

class ProcessMediatorDaemon(private val server: Server) {
  fun start() {
    server.start()
    println("Started server on port ${server.port}")
    Runtime.getRuntime().addShutdownHook(
      Thread {
        println("*** shutting down gRPC server since JVM is shutting down")
        this@ProcessMediatorDaemon.stop()
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
}

private fun createServer(host: String, port: Int): Server {
  return NettyServerBuilder.forAddress(InetSocketAddress(host, port))
    .addService(ProcessMediatorServerService.createServiceDefinition())
    .build()
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

  val server = ProcessMediatorDaemon(createServer(host, port))
  server.start()
  server.blockUntilShutdown()
}