// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.daemon

import com.intellij.execution.process.elevation.rpc.ElevatorGrpcKt
import com.intellij.execution.process.elevation.rpc.SpawnReply
import com.intellij.execution.process.elevation.rpc.SpawnRequest
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import java.net.InetSocketAddress
import kotlin.system.exitProcess

private class ElevatorServer constructor(private val host: String,
                                         private val port: Int) {
  private val server: Server = NettyServerBuilder
    .forAddress(InetSocketAddress(host, port))
    .addService(ElevatorService())
    .build()

  fun start() {
    println("Starting server on $host:$port")
    server.start()
    println("Server started, listening on $host:$port")
    Runtime.getRuntime().addShutdownHook(
      Thread {
        println("*** shutting down gRPC server since JVM is shutting down")
        this@ElevatorServer.stop()
        println("*** server shut down")
      }
    )
  }

  private fun stop() {
    server.shutdown()
  }

  fun blockUntilShutdown() {
    server.awaitTermination()
  }

  private class ElevatorService : ElevatorGrpcKt.ElevatorCoroutineImplBase() {
    override suspend fun spawn(request: SpawnRequest): SpawnReply {
      return SpawnReply.newBuilder()
        .setPid(42)
        .build()
    }
  }
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