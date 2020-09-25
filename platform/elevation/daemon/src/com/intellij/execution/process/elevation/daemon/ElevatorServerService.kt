// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.daemon

import com.intellij.execution.process.elevation.rpc.*
import io.grpc.ServerInterceptors
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import java.io.File
import java.io.IOException

internal class ElevatorServerService : ElevatorGrpcKt.ElevatorCoroutineImplBase() {
  private val processManager = ElevatorProcessManager()

  override suspend fun spawn(request: SpawnRequest): SpawnReply {
    val commandLine = request.commandLine

    val pid = try {
      processManager.createProcess(commandLine.commandList,
                                   File(commandLine.workingDir),
                                   commandLine.environVarsList.associate { it.name to it.value })

    }
    catch (e: IOException) {
      throw StatusException(Status.NOT_FOUND.withCause(e))
    }

    return SpawnReply.newBuilder()
      .setPid(pid)
      .build()
  }

  override suspend fun await(request: AwaitRequest): AwaitReply {
    val exitCode = processManager.awaitTermination(request.pid)
    return AwaitReply.newBuilder()
      .setExitCode(exitCode)
      .build()
  }

  companion object {
    fun createServiceDefinition(): ServerServiceDefinition {
      val service = ElevatorServerService()
      return ServerInterceptors.intercept(service, ExceptionStatusDescriptionAugmenterServerInterceptor)
    }
  }
}
