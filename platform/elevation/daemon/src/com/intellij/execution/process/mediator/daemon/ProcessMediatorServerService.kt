// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.rpc.*
import com.intellij.execution.process.mediator.util.ExceptionStatusDescriptionAugmenterServerInterceptor
import io.grpc.ServerInterceptors
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.IOException

internal class ProcessMediatorServerService : ProcessMediatorGrpcKt.ProcessMediatorCoroutineImplBase() {
  private val processManager = ProcessMediatorProcessManager()

  override suspend fun createProcess(request: CreateProcessRequest): CreateProcessReply {
    val commandLine = request.commandLine

    val pid = try {
      processManager.createProcess(commandLine.commandList,
                                   File(commandLine.workingDir),
                                   commandLine.environVarsList.associate { it.name to it.value },
                                   commandLine.inFile.takeUnless { it.isEmpty() }?.let { File(it) },
                                   commandLine.outFile.takeUnless { it.isEmpty() }?.let { File(it) },
                                   commandLine.errFile.takeUnless { it.isEmpty() }?.let { File(it) })

    }
    catch (e: IOException) {
      throw StatusException(Status.NOT_FOUND.withCause(e))
    }

    return CreateProcessReply.newBuilder()
      .setPid(pid)
      .build()
  }

  override suspend fun destroyProcess(request: DestroyProcessRequest): Empty {
    processManager.destroyProcess(request.pid, true)
    return Empty.getDefaultInstance()
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

  override fun readStream(request: ReadStreamRequest): Flow<DataChunk> {
    val handle = request.handle
    return processManager.readStream(handle.pid, handle.fd)
      .map { buffer ->
        DataChunk.newBuilder()
          .setBuffer(buffer)
          .build()
      }
  }

  override suspend fun writeStream(requests: Flow<WriteStreamRequest>): Empty = coroutineScope {
    @Suppress("EXPERIMENTAL_API_USAGE")
    val receiveChannel = requests.produceIn(this)

    val handle = receiveChannel.receive().also { request ->
      require(request.hasHandle()) { "the first request must specify the handle" }
    }.handle

    val chunkFlow = receiveChannel.consumeAsFlow().mapNotNull { request ->
      require(request.hasChunk()) { "got handle in the middle of the stream" }
      request.chunk.buffer
    }

    processManager.writeStream(handle.pid, handle.fd, chunkFlow)

    return@coroutineScope Empty.getDefaultInstance()
  }

  companion object {
    fun createServiceDefinition(): ServerServiceDefinition {
      val service = ProcessMediatorServerService()
      return ServerInterceptors.intercept(service, ExceptionStatusDescriptionAugmenterServerInterceptor)
    }
  }
}
