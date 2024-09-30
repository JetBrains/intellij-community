package org.jetbrains.embeddings.local.server.stubs

import io.grpc.CallOptions
import io.grpc.CallOptions.DEFAULT
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.ServerServiceDefinition.builder
import io.grpc.ServiceDescriptor
import io.grpc.Status
import io.grpc.Status.UNIMPLEMENTED
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.embeddings.local.server.stubs.embedding_serviceGrpc.getServiceDescriptor

/**
 * Holder for Kotlin coroutine-based client and server APIs for
 * org.jetbrains.embeddings.local.server.stubs.embedding_service.
 */
public object embedding_serviceGrpcKt {
  public const val SERVICE_NAME: String = embedding_serviceGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = embedding_serviceGrpc.getServiceDescriptor()

  public val ensureVectorsPresentMethod:
      MethodDescriptor<Embeddings.present_request, Embeddings.present_response>
    @JvmStatic
    get() = embedding_serviceGrpc.getEnsureVectorsPresentMethod()

  public val removeVectorsMethod:
      MethodDescriptor<Embeddings.remove_request, Embeddings.remove_response>
    @JvmStatic
    get() = embedding_serviceGrpc.getRemoveVectorsMethod()

  public val searchMethod: MethodDescriptor<Embeddings.search_request, Embeddings.search_response>
    @JvmStatic
    get() = embedding_serviceGrpc.getSearchMethod()

  public val clearStorageMethod:
      MethodDescriptor<Embeddings.clear_request, Embeddings.clear_response>
    @JvmStatic
    get() = embedding_serviceGrpc.getClearStorageMethod()

  public val startIndexingSessionMethod:
      MethodDescriptor<Embeddings.start_request, Embeddings.start_response>
    @JvmStatic
    get() = embedding_serviceGrpc.getStartIndexingSessionMethod()

  public val finishIndexingSessionMethod:
      MethodDescriptor<Embeddings.finish_request, Embeddings.finish_response>
    @JvmStatic
    get() = embedding_serviceGrpc.getFinishIndexingSessionMethod()

  public val getStorageStatsMethod:
      MethodDescriptor<Embeddings.stats_request, Embeddings.stats_response>
    @JvmStatic
    get() = embedding_serviceGrpc.getGetStorageStatsMethod()

  /**
   * A stub for issuing RPCs to a(n) org.jetbrains.embeddings.local.server.stubs.embedding_service
   * service as suspending coroutines.
   */
  @StubFor(embedding_serviceGrpc::class)
  public class embedding_serviceCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<embedding_serviceCoroutineStub>(channel, callOptions) {
    public override fun build(channel: Channel, callOptions: CallOptions):
        embedding_serviceCoroutineStub = embedding_serviceCoroutineStub(channel, callOptions)

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun ensureVectorsPresent(request: Embeddings.present_request, headers: Metadata =
        Metadata()): Embeddings.present_response = unaryRpc(
      channel,
      embedding_serviceGrpc.getEnsureVectorsPresentMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun removeVectors(request: Embeddings.remove_request, headers: Metadata =
        Metadata()): Embeddings.remove_response = unaryRpc(
      channel,
      embedding_serviceGrpc.getRemoveVectorsMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun search(request: Embeddings.search_request, headers: Metadata = Metadata()):
        Embeddings.search_response = unaryRpc(
      channel,
      embedding_serviceGrpc.getSearchMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun clearStorage(request: Embeddings.clear_request, headers: Metadata =
        Metadata()): Embeddings.clear_response = unaryRpc(
      channel,
      embedding_serviceGrpc.getClearStorageMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun startIndexingSession(request: Embeddings.start_request, headers: Metadata =
        Metadata()): Embeddings.start_response = unaryRpc(
      channel,
      embedding_serviceGrpc.getStartIndexingSessionMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun finishIndexingSession(request: Embeddings.finish_request, headers: Metadata =
        Metadata()): Embeddings.finish_response = unaryRpc(
      channel,
      embedding_serviceGrpc.getFinishIndexingSessionMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun getStorageStats(request: Embeddings.stats_request, headers: Metadata =
        Metadata()): Embeddings.stats_response = unaryRpc(
      channel,
      embedding_serviceGrpc.getGetStorageStatsMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the org.jetbrains.embeddings.local.server.stubs.embedding_service
   * service based on Kotlin coroutines.
   */
  public abstract class embedding_serviceCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for
     * org.jetbrains.embeddings.local.server.stubs.embedding_service.ensure_vectors_present.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun ensureVectorsPresent(request: Embeddings.present_request):
        Embeddings.present_response = throw
        StatusException(UNIMPLEMENTED.withDescription("Method org.jetbrains.embeddings.local.server.stubs.embedding_service.ensure_vectors_present is unimplemented"))

    /**
     * Returns the response to an RPC for
     * org.jetbrains.embeddings.local.server.stubs.embedding_service.remove_vectors.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun removeVectors(request: Embeddings.remove_request):
        Embeddings.remove_response = throw
        StatusException(UNIMPLEMENTED.withDescription("Method org.jetbrains.embeddings.local.server.stubs.embedding_service.remove_vectors is unimplemented"))

    /**
     * Returns the response to an RPC for
     * org.jetbrains.embeddings.local.server.stubs.embedding_service.search.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun search(request: Embeddings.search_request): Embeddings.search_response =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method org.jetbrains.embeddings.local.server.stubs.embedding_service.search is unimplemented"))

    /**
     * Returns the response to an RPC for
     * org.jetbrains.embeddings.local.server.stubs.embedding_service.clear_storage.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun clearStorage(request: Embeddings.clear_request):
        Embeddings.clear_response = throw
        StatusException(UNIMPLEMENTED.withDescription("Method org.jetbrains.embeddings.local.server.stubs.embedding_service.clear_storage is unimplemented"))

    /**
     * Returns the response to an RPC for
     * org.jetbrains.embeddings.local.server.stubs.embedding_service.start_indexing_session.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun startIndexingSession(request: Embeddings.start_request):
        Embeddings.start_response = throw
        StatusException(UNIMPLEMENTED.withDescription("Method org.jetbrains.embeddings.local.server.stubs.embedding_service.start_indexing_session is unimplemented"))

    /**
     * Returns the response to an RPC for
     * org.jetbrains.embeddings.local.server.stubs.embedding_service.finish_indexing_session.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun finishIndexingSession(request: Embeddings.finish_request):
        Embeddings.finish_response = throw
        StatusException(UNIMPLEMENTED.withDescription("Method org.jetbrains.embeddings.local.server.stubs.embedding_service.finish_indexing_session is unimplemented"))

    /**
     * Returns the response to an RPC for
     * org.jetbrains.embeddings.local.server.stubs.embedding_service.get_storage_stats.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun getStorageStats(request: Embeddings.stats_request):
        Embeddings.stats_response = throw
        StatusException(UNIMPLEMENTED.withDescription("Method org.jetbrains.embeddings.local.server.stubs.embedding_service.get_storage_stats is unimplemented"))

    public final override fun bindService(): ServerServiceDefinition =
        builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = embedding_serviceGrpc.getEnsureVectorsPresentMethod(),
      implementation = ::ensureVectorsPresent
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = embedding_serviceGrpc.getRemoveVectorsMethod(),
      implementation = ::removeVectors
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = embedding_serviceGrpc.getSearchMethod(),
      implementation = ::search
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = embedding_serviceGrpc.getClearStorageMethod(),
      implementation = ::clearStorage
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = embedding_serviceGrpc.getStartIndexingSessionMethod(),
      implementation = ::startIndexingSession
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = embedding_serviceGrpc.getFinishIndexingSessionMethod(),
      implementation = ::finishIndexingSession
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = embedding_serviceGrpc.getGetStorageStatsMethod(),
      implementation = ::getStorageStats
    )).build()
  }
}
