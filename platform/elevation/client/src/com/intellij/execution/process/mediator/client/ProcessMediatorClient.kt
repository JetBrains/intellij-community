// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.client

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.execution.process.mediator.grpc.ExceptionAsStatus
import com.intellij.execution.process.mediator.grpc.LoggingClientInterceptor
import com.intellij.execution.process.mediator.rpc.*
import com.intellij.execution.process.mediator.util.childSupervisorScope
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit

class ProcessMediatorClient(
  coroutineScope: CoroutineScope,
  private val channel: ManagedChannel,
  initialQuotaOptions: QuotaOptions = QuotaOptions.UNLIMITED,
) : CoroutineScope by coroutineScope.childSupervisorScope(),
    Closeable {
  private val loggingChannel = ClientInterceptors.intercept(channel, LoggingClientInterceptor)

  private val processManagerStub = ProcessManagerGrpcKt.ProcessManagerCoroutineStub(loggingChannel)
  private val daemonStub = DaemonGrpcKt.DaemonCoroutineStub(loggingChannel)

  init {
    // send this request even if doesn't really change the quota, just so we know the server is up and running
    adjustQuotaBlocking(initialQuotaOptions)
  }

  suspend fun createProcess(command: List<String>, workingDir: File, environVars: Map<String, String>,
                            inFile: File?, outFile: File?, errFile: File?): Long {
    val commandLine = CommandLine.newBuilder()
      .addAllCommand(command)
      .setWorkingDir(workingDir.absolutePath)
      .putAllEnviron(environVars)
      .apply {
        inFile?.let { setInFile(it.absolutePath) }
        outFile?.let { setOutFile(it.absolutePath) }
        errFile?.let { setErrFile(it.absolutePath) }
      }
      .build()
    val request = CreateProcessRequest.newBuilder().setCommandLine(commandLine).build()
    val response = ExceptionAsStatus.unwrap {
      processManagerStub.createProcess(request)
    }
    return response.pid
  }

  suspend fun destroyProcess(pid: Long, force: Boolean, destroyGroup: Boolean) {
    val request = DestroyProcessRequest.newBuilder()
      .setPid(pid)
      .setForce(force)
      .setDestroyGroup(destroyGroup)
      .build()
    ExceptionAsStatus.unwrap {
      processManagerStub.destroyProcess(request)
    }
  }

  suspend fun awaitTermination(pid: Long): Int {
    val request = AwaitTerminationRequest.newBuilder()
      .setPid(pid)
      .build()
    val reply = ExceptionAsStatus.unwrap {
      processManagerStub.awaitTermination(request)
    }
    return reply.exitCode
  }

  fun readStream(pid: Long, fd: Int): Flow<ByteString> {
    val handle = FileHandle.newBuilder()
      .setPid(pid)
      .setFd(fd)
      .build()
    val request = ReadStreamRequest.newBuilder()
      .setHandle(handle)
      .build()
    val chunkFlow = ExceptionAsStatus.unwrap {
      processManagerStub.readStream(request)
    }
    return chunkFlow.map { chunk ->
      chunk.buffer
    }.catch { cause ->
      ExceptionAsStatus.unwrap { throw cause }
    }
  }

  suspend fun writeStream(pid: Long, fd: Int, chunkFlow: Flow<ByteString>): Flow<Unit> {
    val handle = FileHandle.newBuilder()
      .setPid(pid)
      .setFd(fd)
      .build()
    val handleRequest = WriteStreamRequest.newBuilder()
      .setHandle(handle)
      .build()

    @Suppress("EXPERIMENTAL_API_USAGE")
    val requests = chunkFlow.map { buffer ->
      val chunk = DataChunk.newBuilder()
        .setBuffer(buffer)
        .build()
      WriteStreamRequest.newBuilder()
        .setChunk(chunk)
        .build()
    }.onStart {
      emit(handleRequest)
    }
    return ExceptionAsStatus.unwrap {
      processManagerStub.writeStream(requests)
    }.map {}.catch { cause ->
      ExceptionAsStatus.unwrap { throw cause }
    }
  }

  suspend fun release(pid: Long) {
    val request = ReleaseRequest.newBuilder()
      .setPid(pid)
      .build()
    ExceptionAsStatus.unwrap { processManagerStub.release(request) }
  }

  suspend fun adjustQuota(newOptions: QuotaOptions) {
    val request = AdjustQuotaRequest.newBuilder()
      .setTimeLimitMs(newOptions.timeLimitMs)
      .setIsRefreshable(newOptions.isRefreshable)
      .build()
    daemonStub.adjustQuota(request)
  }

  private suspend fun shutdown() {
    daemonStub.shutdown(Empty.getDefaultInstance())
  }

  fun adjustQuotaBlocking(newOptions: QuotaOptions) {
    runBlocking(coroutineContext) {
      adjustQuota(newOptions)
    }
  }

  override fun close() {
    try {
      runBlocking(coroutineContext) {
        shutdown()
      }
    }
    finally {
      cancel()
      channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
  }
}
