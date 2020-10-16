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
  System.err.println(message)
  System.err.println("Usage: $programName < --hello-file=file|- | --hello-port=port >")
  exitProcess(1)
}

fun main(args: Array<String>) {
  var helloWriter: DaemonHelloWriter? = null
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
        if (helloWriter != null) System.err.println("Ignoring '$option'")
        else helloWriter = DaemonHelloSocketWriter(value.toUShort())
      }

      null -> die("Unrecognized positional argument '$value'")
      else -> die("Unrecognized option '$option'")
    }
  }
  if (helloWriter == null) die("Missing required option '--hello-file' or '--hello-port'")

  val daemon = try {
    helloWriter.use {
      ProcessMediatorServerDaemon(ServerBuilder.forPort(0)).also { daemon ->
        val daemonHello = DaemonHello.newBuilder()
          .setPort(daemon.port)
          .build()

        try {
          helloWriter.write(daemonHello::writeDelimitedTo)
        }
        catch (e: Throwable) {
          daemon.stop()
          throw e
        }
      }
    }
  }
  catch (e: IOException) {
    die("Unable to write hello: ${e.message}")
  }
  daemon.blockUntilShutdown()
}
