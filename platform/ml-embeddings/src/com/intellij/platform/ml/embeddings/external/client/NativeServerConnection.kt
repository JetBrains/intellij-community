// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.external.client

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.platform.ml.embeddings.external.client.listeners.ServerDiagnosticsListener
import com.intellij.platform.ml.embeddings.external.client.listeners.StartupInformationListener
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.*
import org.jetbrains.embeddings.local.server.stubs.Embeddings
import org.jetbrains.embeddings.local.server.stubs.embedding_serviceGrpcKt
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.time.Duration

typealias GrpcStub = embedding_serviceGrpcKt.embedding_serviceCoroutineStub

typealias EmbeddingsPresentRequest = Embeddings.present_request
typealias EmbeddingsPresentResponse = Embeddings.present_response

typealias EmbeddingsRemoveRequest = Embeddings.remove_request
typealias EmbeddingsRemoveResponse = Embeddings.remove_response

typealias EmbeddingsSearchRequest = Embeddings.search_request
typealias EmbeddingsSearchResponse = Embeddings.search_response

typealias EmbeddingsClearRequest = Embeddings.clear_request
typealias EmbeddingsClearResponse = Embeddings.clear_response

typealias EmbeddingsStartRequest = Embeddings.start_request
typealias EmbeddingsStartResponse = Embeddings.start_response

typealias EmbeddingsFinishRequest = Embeddings.finish_request
typealias EmbeddingsFinishResponse = Embeddings.finish_response

typealias EmbeddingsStatsRequest = Embeddings.stats_request
typealias EmbeddingsStatsResponse = Embeddings.stats_response

typealias EmbeddingsStorageLocation = Embeddings.storage_location

class NativeServerConnection private constructor(
  private val osProcessHandler: OSProcessHandler,
  private val channel: ManagedChannel,
  private val stub: GrpcStub,
) {

  suspend fun ensureVectorsPresent(request: EmbeddingsPresentRequest): EmbeddingsPresentResponse {
    return boxGrpcException {
      stub.ensureVectorsPresent(request)
    }
  }

  suspend fun removeVectors(request: EmbeddingsRemoveRequest): EmbeddingsRemoveResponse {
    return boxGrpcException {
      stub.removeVectors(request)
    }
  }

  suspend fun search(request: EmbeddingsSearchRequest): EmbeddingsSearchResponse {
    return boxGrpcException {
      stub.search(request)
    }
  }

  suspend fun clearStorage(request: EmbeddingsClearRequest): EmbeddingsClearResponse {
    return boxGrpcException {
      stub.clearStorage(request)
    }
  }

  suspend fun startIndexingSession(request: EmbeddingsStartRequest): EmbeddingsStartResponse {
    return boxGrpcException {
      stub.startIndexingSession(request)
    }
  }

  suspend fun finishIndexingSession(request: EmbeddingsFinishRequest): EmbeddingsFinishResponse {
    return boxGrpcException {
      stub.finishIndexingSession(request)
    }
  }

  suspend fun getStorageStats(request: EmbeddingsStatsRequest): EmbeddingsStatsResponse {
    return boxGrpcException {
      stub.getStorageStats(request)
    }
  }

  fun shutdown() {
    channel.shutdown()
    channel.awaitTermination(100, TimeUnit.MILLISECONDS)
    channel.shutdownNow()
    osProcessHandler.destroyProcess()
  }

  private inline fun <T> boxGrpcException(block: () -> T): T = try {
    block()
  }
  catch (e: StatusException) {
    throw NativeServerException(e)
  }

  companion object {
    private const val LOCAL_HOSTNAME = "localhost"
    private const val CLIENT_MAX_SEND_MESSAGE_LENGTH = 1024 * 1024

    // TODO: add server signature check
    suspend fun create(
      serverPath: Path,
      startupArguments: NativeServerStartupArguments,
      startupTimeout: Duration,
      onStartupTimeout: () -> Unit,
    ): NativeServerConnection = withContext(Dispatchers.Default) {
      if (!serverPath.exists()) throw NativeServerException(IllegalArgumentException("Server file does not exist"))
      if (!serverPath.isRegularFile()) throw NativeServerException(IllegalArgumentException("Server file is not a regular file"))
      serverPath.toFile().setExecutable(true)

      val commandLine = GeneralCommandLine().withExePath(serverPath.absolutePathString()).withParameters(startupArguments.combine())
      val processHandler = OSProcessHandler.Silent(commandLine)

      val startupInformationListener = StartupInformationListener()
      val diagnosticsListener = ServerDiagnosticsListener()
      // TODO: add reporting to FUS logs
      processHandler.addProcessListener(diagnosticsListener)
      processHandler.addProcessListener(startupInformationListener)
      processHandler.startNotify()

      val port = withTimeoutOrNull(startupTimeout) { startupInformationListener.waitForTheStart() }
      if (port == null) {
        onStartupTimeout()
        throw NativeServerException(IllegalStateException("Cannot start the native server"))
      }

      processHandler.removeProcessListener(startupInformationListener)

      val channel = ManagedChannelBuilder
        .forAddress(LOCAL_HOSTNAME, port)
        .usePlaintext()
        .maxInboundMessageSize(CLIENT_MAX_SEND_MESSAGE_LENGTH)
        .executor(Dispatchers.IO.asExecutor())
        .build()

      for (state in ConnectivityState.entries) {
        channel.notifyWhenStateChanged(state) {
           diagnosticsListener.grpcChannelStateChanged(state.name)
        }
      }
      diagnosticsListener.connectionAddress(LOCAL_HOSTNAME, port)

      NativeServerConnection(processHandler, channel, GrpcStub(channel))
    }
  }
}