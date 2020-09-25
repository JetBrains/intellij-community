// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation.daemon

import com.intellij.execution.process.elevation.rpc.ElevatorGrpcKt
import com.intellij.execution.process.elevation.rpc.SpawnReply
import com.intellij.execution.process.elevation.rpc.SpawnRequest
import io.grpc.Status
import io.grpc.StatusException
import java.io.IOException

internal class ElevatorServerService : ElevatorGrpcKt.ElevatorCoroutineImplBase() {
  private val processManager = ElevatorProcessManager()

  override suspend fun spawn(request: SpawnRequest): SpawnReply {
    val commandLine = request.commandLine
    val command = arrayOf(commandLine.exePath,
                          *commandLine.argumentsList.toTypedArray())

    try {
      val pid = processManager.createProcess(command)

      return SpawnReply.newBuilder()
        .setPid(pid.toInt())
        .build()
    }
    catch (e: IOException) {
      throw StatusException(Status.NOT_FOUND.withCause(e))
    }
  }
}
