// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.elevation.rpc.*
import io.grpc.ServerInterceptors
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import java.io.File
import java.io.IOException

internal class ElevatorServerService : ElevatorGrpcKt.ElevatorCoroutineImplBase() {
  private val processManager = ElevatorProcessManager()

  override suspend fun createProcess(request: CreateProcessRequest): CreateProcessReply {
    val commandLine = request.commandLine

    val pid = try {
      processManager.createProcess(commandLine.commandList,
                                   File(commandLine.workingDir),
                                   commandLine.environVarsList.associate { it.name to it.value })

    }
    catch (e: IOException) {
      throw StatusException(Status.NOT_FOUND.withCause(e))
    }

    return CreateProcessReply.newBuilder()
      .setPid(pid)
      .build()
  }

  override suspend fun awaitTermination(request: AwaitTerminationRequest): AwaitTerminationReply {
    val exitCode = processManager.awaitTermination(request.pid)
    return AwaitTerminationReply.newBuilder()
      .setExitCode(exitCode)
      .build()
  }

  override suspend fun release(request: ReleaseRequest): Empty {
    processManager.release(request.pid)
    return Empty.getDefaultInstance()
  }

  companion object {
    fun createServiceDefinition(): ServerServiceDefinition {
      val service = ElevatorServerService()
      return ServerInterceptors.intercept(service, ExceptionStatusDescriptionAugmenterServerInterceptor)
    }
  }
}
