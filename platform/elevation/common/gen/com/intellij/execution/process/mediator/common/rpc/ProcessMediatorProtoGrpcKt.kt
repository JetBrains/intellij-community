package com.intellij.execution.process.mediator.common.rpc

import com.google.protobuf.Empty
import io.grpc.CallOptions
import io.grpc.CallOptions.DEFAULT
import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.ServerServiceDefinition.builder
import io.grpc.ServiceDescriptor
import io.grpc.Status.UNIMPLEMENTED
import io.grpc.StatusException
import io.grpc.kotlin.AbstractCoroutineServerImpl
import io.grpc.kotlin.AbstractCoroutineStub
import io.grpc.kotlin.ClientCalls.bidiStreamingRpc
import io.grpc.kotlin.ClientCalls.serverStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls.bidiStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.serverStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.String
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow
import com.intellij.execution.process.mediator.common.rpc.DaemonGrpc.getServiceDescriptor as daemonGrpcGetServiceDescriptor
import com.intellij.execution.process.mediator.common.rpc.ProcessManagerGrpc.getServiceDescriptor as processManagerGrpcGetServiceDescriptor

/**
 * Holder for Kotlin coroutine-based client and server APIs for
 * intellij.process.mediator.common.rpc.Daemon.
 */
public object DaemonGrpcKt {
  public const val SERVICE_NAME: String = DaemonGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = daemonGrpcGetServiceDescriptor()

  public val adjustQuotaMethod: MethodDescriptor<QuotaOptions, Empty>
    @JvmStatic
    get() = DaemonGrpc.getAdjustQuotaMethod()

  public val listenQuotaStateUpdatesMethod: MethodDescriptor<Empty, QuotaState>
    @JvmStatic
    get() = DaemonGrpc.getListenQuotaStateUpdatesMethod()

  public val shutdownMethod: MethodDescriptor<Empty, Empty>
    @JvmStatic
    get() = DaemonGrpc.getShutdownMethod()

  /**
   * A stub for issuing RPCs to a(n) intellij.process.mediator.common.rpc.Daemon service as
   * suspending coroutines.
   */
  @StubFor(DaemonGrpc::class)
  public class DaemonCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<DaemonCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): DaemonCoroutineStub =
        DaemonCoroutineStub(channel, callOptions)

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun adjustQuota(request: QuotaOptions, headers: Metadata = Metadata()): Empty =
        unaryRpc(
      channel,
      DaemonGrpc.getAdjustQuotaMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun listenQuotaStateUpdates(request: Empty, headers: Metadata = Metadata()):
        Flow<QuotaState> = serverStreamingRpc(
      channel,
      DaemonGrpc.getListenQuotaStateUpdatesMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun shutdown(request: Empty, headers: Metadata = Metadata()): Empty = unaryRpc(
      channel,
      DaemonGrpc.getShutdownMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the intellij.process.mediator.common.rpc.Daemon service based on
   * Kotlin coroutines.
   */
  public abstract class DaemonCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for intellij.process.mediator.common.rpc.Daemon.AdjustQuota.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun adjustQuota(request: QuotaOptions): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.Daemon.AdjustQuota is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.common.rpc.Daemon.ListenQuotaStateUpdates.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open fun listenQuotaStateUpdates(request: Empty): Flow<QuotaState> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.Daemon.ListenQuotaStateUpdates is unimplemented"))

    /**
     * Returns the response to an RPC for intellij.process.mediator.common.rpc.Daemon.Shutdown.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun shutdown(request: Empty): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.Daemon.Shutdown is unimplemented"))

    final override fun bindService(): ServerServiceDefinition =
        builder(daemonGrpcGetServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = DaemonGrpc.getAdjustQuotaMethod(),
      implementation = ::adjustQuota
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = DaemonGrpc.getListenQuotaStateUpdatesMethod(),
      implementation = ::listenQuotaStateUpdates
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = DaemonGrpc.getShutdownMethod(),
      implementation = ::shutdown
    )).build()
  }
}

/**
 * Holder for Kotlin coroutine-based client and server APIs for
 * intellij.process.mediator.common.rpc.ProcessManager.
 */
public object ProcessManagerGrpcKt {
  public const val SERVICE_NAME: String = ProcessManagerGrpc.SERVICE_NAME

  @JvmStatic
  public val serviceDescriptor: ServiceDescriptor
    get() = processManagerGrpcGetServiceDescriptor()

  public val openHandleMethod: MethodDescriptor<Empty, OpenHandleReply>
    @JvmStatic
    get() = ProcessManagerGrpc.getOpenHandleMethod()

  public val createProcessMethod: MethodDescriptor<CreateProcessRequest, CreateProcessReply>
    @JvmStatic
    get() = ProcessManagerGrpc.getCreateProcessMethod()

  public val destroyProcessMethod: MethodDescriptor<DestroyProcessRequest, Empty>
    @JvmStatic
    get() = ProcessManagerGrpc.getDestroyProcessMethod()

  public val awaitTerminationMethod:
      MethodDescriptor<AwaitTerminationRequest, AwaitTerminationReply>
    @JvmStatic
    get() = ProcessManagerGrpc.getAwaitTerminationMethod()

  public val writeStreamMethod: MethodDescriptor<WriteStreamRequest, Empty>
    @JvmStatic
    get() = ProcessManagerGrpc.getWriteStreamMethod()

  public val readStreamMethod: MethodDescriptor<ReadStreamRequest, DataChunk>
    @JvmStatic
    get() = ProcessManagerGrpc.getReadStreamMethod()

  /**
   * A stub for issuing RPCs to a(n) intellij.process.mediator.common.rpc.ProcessManager service as
   * suspending coroutines.
   */
  @StubFor(ProcessManagerGrpc::class)
  public class ProcessManagerCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT,
  ) : AbstractCoroutineStub<ProcessManagerCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): ProcessManagerCoroutineStub =
        ProcessManagerCoroutineStub(channel, callOptions)

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun openHandle(request: Empty, headers: Metadata = Metadata()): Flow<OpenHandleReply> =
        serverStreamingRpc(
      channel,
      ProcessManagerGrpc.getOpenHandleMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun createProcess(request: CreateProcessRequest, headers: Metadata = Metadata()):
        CreateProcessReply = unaryRpc(
      channel,
      ProcessManagerGrpc.getCreateProcessMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun destroyProcess(request: DestroyProcessRequest, headers: Metadata =
        Metadata()): Empty = unaryRpc(
      channel,
      ProcessManagerGrpc.getDestroyProcessMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][io.grpc.Status].  If the RPC completes with another status, a
     * corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return The single response from the server.
     */
    public suspend fun awaitTermination(request: AwaitTerminationRequest, headers: Metadata =
        Metadata()): AwaitTerminationReply = unaryRpc(
      channel,
      ProcessManagerGrpc.getAwaitTerminationMethod(),
      request,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * The [Flow] of requests is collected once each time the [Flow] of responses is
     * collected. If collection of the [Flow] of responses completes normally or
     * exceptionally before collection of `requests` completes, the collection of
     * `requests` is cancelled.  If the collection of `requests` completes
     * exceptionally for any other reason, then the collection of the [Flow] of responses
     * completes exceptionally for the same reason and the RPC is cancelled with that reason.
     *
     * @param requests A [Flow] of request messages.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun writeStream(requests: Flow<WriteStreamRequest>, headers: Metadata = Metadata()):
        Flow<Empty> = bidiStreamingRpc(
      channel,
      ProcessManagerGrpc.getWriteStreamMethod(),
      requests,
      callOptions,
      headers
    )

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][io.grpc.Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @param headers Metadata to attach to the request.  Most users will not need this.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    public fun readStream(request: ReadStreamRequest, headers: Metadata = Metadata()):
        Flow<DataChunk> = serverStreamingRpc(
      channel,
      ProcessManagerGrpc.getReadStreamMethod(),
      request,
      callOptions,
      headers
    )
  }

  /**
   * Skeletal implementation of the intellij.process.mediator.common.rpc.ProcessManager service
   * based on Kotlin coroutines.
   */
  public abstract class ProcessManagerCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.common.rpc.ProcessManager.OpenHandle.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open fun openHandle(request: Empty): Flow<OpenHandleReply> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.ProcessManager.OpenHandle is unimplemented"))

    /**
     * Returns the response to an RPC for
     * intellij.process.mediator.common.rpc.ProcessManager.CreateProcess.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun createProcess(request: CreateProcessRequest): CreateProcessReply = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.ProcessManager.CreateProcess is unimplemented"))

    /**
     * Returns the response to an RPC for
     * intellij.process.mediator.common.rpc.ProcessManager.DestroyProcess.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun destroyProcess(request: DestroyProcessRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.ProcessManager.DestroyProcess is unimplemented"))

    /**
     * Returns the response to an RPC for
     * intellij.process.mediator.common.rpc.ProcessManager.AwaitTermination.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [io.grpc.Status].  If this method fails with a [java.util.concurrent.CancellationException],
     * the RPC will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open suspend fun awaitTermination(request: AwaitTerminationRequest):
        AwaitTerminationReply = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.ProcessManager.AwaitTermination is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.common.rpc.ProcessManager.WriteStream.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param requests A [Flow] of requests from the client.  This flow can be
     *        collected only once and throws [java.lang.IllegalStateException] on attempts to
     * collect
     *        it more than once.
     */
    public open fun writeStream(requests: Flow<WriteStreamRequest>): Flow<Empty> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.ProcessManager.WriteStream is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.common.rpc.ProcessManager.ReadStream.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [io.grpc.Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    public open fun readStream(request: ReadStreamRequest): Flow<DataChunk> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.common.rpc.ProcessManager.ReadStream is unimplemented"))

    final override fun bindService(): ServerServiceDefinition =
        builder(processManagerGrpcGetServiceDescriptor())
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = ProcessManagerGrpc.getOpenHandleMethod(),
      implementation = ::openHandle
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ProcessManagerGrpc.getCreateProcessMethod(),
      implementation = ::createProcess
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ProcessManagerGrpc.getDestroyProcessMethod(),
      implementation = ::destroyProcess
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ProcessManagerGrpc.getAwaitTerminationMethod(),
      implementation = ::awaitTermination
    ))
      .addMethod(bidiStreamingServerMethodDefinition(
      context = this.context,
      descriptor = ProcessManagerGrpc.getWriteStreamMethod(),
      implementation = ::writeStream
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = ProcessManagerGrpc.getReadStreamMethod(),
      implementation = ::readStream
    )).build()
  }
}
