// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.grpc.ExceptionAsStatus
import com.intellij.execution.process.mediator.grpc.ExceptionStatusDescriptionAugmenterServerInterceptor
import com.intellij.execution.process.mediator.rpc.*
import io.grpc.ServerInterceptors
import io.grpc.ServerServiceDefinition
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.*
import java.io.File

internal class ProcessManagerServerService(
  private val processManager: ProcessManager,
  private val quotaManager: QuotaManager,
) : ProcessManagerGrpcKt.ProcessManagerCoroutineImplBase() {

  override suspend fun createProcess(request: CreateProcessRequest): CreateProcessReply {
    val commandLine = request.commandLine

    val pid = ExceptionAsStatus.wrap {
      quotaManager.runIfPermitted {
        processManager.createProcess(commandLine.commandList,
                                     File(commandLine.workingDir),
                                     commandLine.environMap,
                                     commandLine.inFile.takeUnless { it.isEmpty() }?.let { File(it) },
                                     commandLine.outFile.takeUnless { it.isEmpty() }?.let { File(it) },
                                     commandLine.errFile.takeUnless { it.isEmpty() }?.let { File(it) })
      } ?: throw QuotaExceededException()
    }

    return CreateProcessReply.newBuilder()
      .setPid(pid)
      .build()
  }

  override suspend fun destroyProcess(request: DestroyProcessRequest): Empty {
    ExceptionAsStatus.wrap {
      processManager.destroyProcess(request.pid, request.force, request.destroyGroup)
    }
    return Empty.getDefaultInstance()
  }

  override suspend fun awaitTermination(request: AwaitTerminationRequest): AwaitTerminationReply {
    val exitCode = ExceptionAsStatus.wrap {
      processManager.awaitTermination(request.pid)
    }
    return AwaitTerminationReply.newBuilder()
      .setExitCode(exitCode)
      .build()
  }

  override suspend fun release(request: ReleaseRequest): Empty {
    ExceptionAsStatus.wrap {
      processManager.release(request.pid)
    }
    return Empty.getDefaultInstance()
  }

  override fun readStream(request: ReadStreamRequest): Flow<DataChunk> {
    val handle = request.handle
    return ExceptionAsStatus.wrap {
      processManager.readStream(handle.pid, handle.fd)
    }.map { buffer ->
      DataChunk.newBuilder()
        .setBuffer(buffer)
        .build()
    }.catch { cause ->
      ExceptionAsStatus.wrap { throw cause }
    }
  }

  override fun writeStream(requests: Flow<WriteStreamRequest>): Flow<Empty> {
    @Suppress("EXPERIMENTAL_API_USAGE")
    return channelFlow<Unit> {
      @Suppress("EXPERIMENTAL_API_USAGE")
      requests.produceIn(this).consume {
        val handle = receive().also { request ->
          require(request.hasHandle()) { "the first request must specify the handle" }
        }.handle

        val chunkFlow = receiveAsFlow().mapNotNull { request ->
          require(request.hasChunk()) { "got handle in the middle of the stream" }
          request.chunk.buffer
        }

        processManager.writeStream(handle.pid, handle.fd, chunkFlow, channel)
      }
    }.map {
      Empty.getDefaultInstance()
    }.catch { cause ->
      ExceptionAsStatus.wrap { throw cause }
    }
  }

  companion object {
    fun createServiceDefinition(processManager: ProcessManager, quotaManager: QuotaManager): ServerServiceDefinition {
      val service = ProcessManagerServerService(processManager, quotaManager)
      return ServerInterceptors
        .intercept(service, ExceptionStatusDescriptionAugmenterServerInterceptor)
    }
  }
}
