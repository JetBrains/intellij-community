// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.client

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.execution.process.mediator.daemon.QuotaState
import com.intellij.execution.process.mediator.grpc.ExceptionAsStatus
import com.intellij.execution.process.mediator.grpc.LoggingClientInterceptor
import com.intellij.execution.process.mediator.rpc.*
import io.grpc.Channel
import io.grpc.ClientInterceptors
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.io.File
import com.intellij.execution.process.mediator.rpc.QuotaOptions as QuotaOptionsMessage

class ProcessMediatorClient private constructor(
  parentScope: CoroutineScope,
  channel: Channel,
  initialQuotaOptions: QuotaOptions,
) : Closeable {
  private val job: CompletableJob = SupervisorJob(parentScope.coroutineContext.job)
  internal val coroutineScope: CoroutineScope = parentScope + job

  private val processManagerStub = ProcessManagerGrpcKt.ProcessManagerCoroutineStub(channel)
  private val daemonStub = DaemonGrpcKt.DaemonCoroutineStub(channel)

  init {
    // send this request even if doesn't really change the quota, just so we know the server is up and running
    adjustQuotaBlocking(initialQuotaOptions)
  }

  private val stateFlow: StateFlow<QuotaState?> = runBlocking(coroutineScope.coroutineContext) {
    listenQuotaStateUpdates()
      .map<QuotaState, QuotaState?> { it }
      .onStart { emit(QuotaState.New(initialQuotaOptions)) }  // to avoid blocking stateIn()
      .onCompletion {
        job.complete()
        emit(null)
      }
      .stateIn(coroutineScope)
  }

  val stateUpdateFlow: Flow<QuotaState>  // unlike StateFlow<*>, this flow completes when the server is done
    get() = stateFlow.takeWhile { it != null }.filterNotNull()

  fun openHandle(): Flow<Long> {
    return ExceptionAsStatus.unwrap {
      processManagerStub.openHandle(Empty.getDefaultInstance())
    }.map {
      it.handleId
    }.catch { cause ->
      ExceptionAsStatus.unwrap { throw cause }
    }
  }

  suspend fun createProcess(handleId: Long,
                            command: List<String>, workingDir: File, environVars: Map<String, String>,
                            inFile: File?, outFile: File?, errFile: File?): Long {
    val commandLine = CommandLine.newBuilder().apply {
      addAllCommand(command)
      this.workingDir = workingDir.absolutePath
      putAllEnviron(environVars)
      inFile?.let { this.inFile = it.absolutePath }
      outFile?.let { this.outFile = it.absolutePath }
      errFile?.let { this.errFile = it.absolutePath }
    }.build()
    val request = CreateProcessRequest.newBuilder().apply {
      this.handleId = handleId
      this.commandLine = commandLine
    }.build()
    val response = ExceptionAsStatus.unwrap {
      processManagerStub.createProcess(request)
    }
    return response.pid
  }

  suspend fun destroyProcess(handleId: Long, force: Boolean, destroyGroup: Boolean) {
    val request = DestroyProcessRequest.newBuilder().apply {
      this.handleId = handleId
      this.force = force
      this.destroyGroup = destroyGroup
    }.build()
    ExceptionAsStatus.unwrap {
      processManagerStub.destroyProcess(request)
    }
  }

  suspend fun awaitTermination(handleId: Long): Int {
    val request = AwaitTerminationRequest.newBuilder().apply {
      this.handleId = handleId
    }.build()
    val reply = ExceptionAsStatus.unwrap {
      processManagerStub.awaitTermination(request)
    }
    return reply.exitCode
  }

  fun readStream(handleId: Long, fd: Int): Flow<ByteString> {
    val handle = FileHandle.newBuilder().apply {
      this.handleId = handleId
      this.fd = fd
    }.build()
    val request = ReadStreamRequest.newBuilder().apply {
      this.handle = handle
    }.build()
    val chunkFlow = ExceptionAsStatus.unwrap {
      processManagerStub.readStream(request)
    }
    return chunkFlow.map { chunk ->
      chunk.buffer
    }.catch { cause ->
      ExceptionAsStatus.unwrap { throw cause }
    }
  }

  fun writeStream(handleId: Long, fd: Int, chunkFlow: Flow<ByteString>): Flow<Unit> {
    val handle = FileHandle.newBuilder().apply {
      this.handleId = handleId
      this.fd = fd
    }.build()
    val handleRequest = WriteStreamRequest.newBuilder().apply {
      this.handle = handle
    }.build()

    @Suppress("EXPERIMENTAL_API_USAGE")
    val requests = chunkFlow.map { buffer ->
      val chunk = DataChunk.newBuilder().apply {
        this.buffer = buffer
      }.build()
      WriteStreamRequest.newBuilder().apply {
        this.chunk = chunk
      }.build()
    }.onStart {
      emit(handleRequest)
    }
    return ExceptionAsStatus.unwrap {
      processManagerStub.writeStream(requests)
    }.map {}.catch { cause ->
      ExceptionAsStatus.unwrap { throw cause }
    }
  }

  private suspend fun adjustQuota(newOptions: QuotaOptions) {
    val request = QuotaOptionsMessage.newBuilder().buildFrom(newOptions)
    ExceptionAsStatus.unwrap { daemonStub.adjustQuota(request) }
  }

  private fun listenQuotaStateUpdates(): Flow<QuotaState> {
    val updateFlow = ExceptionAsStatus.unwrap {
      daemonStub.listenQuotaStateUpdates(Empty.getDefaultInstance())
    }
    return updateFlow.map {
      it.toQuotaState()
    }.catch { cause ->
      ExceptionAsStatus.unwrap { throw cause }
    }
  }

  private suspend fun shutdown() {
    ExceptionAsStatus.unwrap { daemonStub.shutdown(Empty.getDefaultInstance()) }
  }

  fun adjustQuotaBlocking(newOptions: QuotaOptions) {
    runBlocking(coroutineScope.coroutineContext) {
      adjustQuota(newOptions)
    }
  }

  override fun close() {
    try {
      runBlocking(coroutineScope.coroutineContext) {
        shutdown()
      }
    }
    finally {
      coroutineScope.cancel()
    }
  }

  data class Builder(private val coroutineScope: CoroutineScope,
                     private val quotaOptions: QuotaOptions = QuotaOptions.UNLIMITED) {
    fun createClient(channel: Channel,
                     credentials: DaemonClientCredentials): ProcessMediatorClient {
      val authInterceptor = MetadataUtils.newAttachHeadersInterceptor(credentials.asMetadata())
      val interceptedChannel = ClientInterceptors.intercept(channel, LoggingClientInterceptor, authInterceptor)

      return ProcessMediatorClient(coroutineScope, interceptedChannel, quotaOptions)
    }
  }
}
