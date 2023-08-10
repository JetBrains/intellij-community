package com.intellij.execution.process.mediator.rpc

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.rpc.DaemonGrpc.getServiceDescriptor
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
import io.grpc.kotlin.ClientCalls.bidiStreamingRpc
import io.grpc.kotlin.ClientCalls.serverStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls
import io.grpc.kotlin.ServerCalls.bidiStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.serverStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

/**
 * Holder for Kotlin coroutine-based client and server APIs for
 * intellij.process.mediator.rpc.Daemon.
 */
object DaemonGrpcKt {
  @JvmStatic
  val serviceDescriptor: ServiceDescriptor
    get() = DaemonGrpc.getServiceDescriptor()

  val adjustQuotaMethod: MethodDescriptor<QuotaOptions, Empty>
    @JvmStatic
    get() = DaemonGrpc.getAdjustQuotaMethod()

  val listenQuotaStateUpdatesMethod: MethodDescriptor<Empty, QuotaState>
    @JvmStatic
    get() = DaemonGrpc.getListenQuotaStateUpdatesMethod()

  val shutdownMethod: MethodDescriptor<Empty, Empty>
    @JvmStatic
    get() = DaemonGrpc.getShutdownMethod()

  /**
   * A stub for issuing RPCs to a(n) intellij.process.mediator.rpc.Daemon service as suspending
   * coroutines.
   */
  @StubFor(DaemonGrpc::class)
  class DaemonCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT
  ) : AbstractCoroutineStub<DaemonCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): DaemonCoroutineStub =
        DaemonCoroutineStub(channel, callOptions)

    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return The single response from the server.
     */
    suspend fun adjustQuota(request: QuotaOptions): Empty = unaryRpc(
      channel,
      DaemonGrpc.getAdjustQuotaMethod(),
      request,
      callOptions,
      Metadata()
    )
    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    fun listenQuotaStateUpdates(request: Empty): Flow<QuotaState> = serverStreamingRpc(
      channel,
      DaemonGrpc.getListenQuotaStateUpdatesMethod(),
      request,
      callOptions,
      Metadata()
    )
    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return The single response from the server.
     */
    suspend fun shutdown(request: Empty): Empty = unaryRpc(
      channel,
      DaemonGrpc.getShutdownMethod(),
      request,
      callOptions,
      Metadata()
    )}

  /**
   * Skeletal implementation of the intellij.process.mediator.rpc.Daemon service based on Kotlin
   * coroutines.
   */
  abstract class DaemonCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for intellij.process.mediator.rpc.Daemon.AdjustQuota.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open suspend fun adjustQuota(request: QuotaOptions): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.Daemon.AdjustQuota is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.rpc.Daemon.ListenQuotaStateUpdates.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open fun listenQuotaStateUpdates(request: Empty): Flow<QuotaState> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.Daemon.ListenQuotaStateUpdates is unimplemented"))

    /**
     * Returns the response to an RPC for intellij.process.mediator.rpc.Daemon.Shutdown.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open suspend fun shutdown(request: Empty): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.Daemon.Shutdown is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
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
 * intellij.process.mediator.rpc.ProcessManager.
 */
object ProcessManagerGrpcKt {
  @JvmStatic
  val serviceDescriptor: ServiceDescriptor
    get() = ProcessManagerGrpc.getServiceDescriptor()

  val openHandleMethod: MethodDescriptor<Empty, OpenHandleReply>
    @JvmStatic
    get() = ProcessManagerGrpc.getOpenHandleMethod()

  val createProcessMethod: MethodDescriptor<CreateProcessRequest, CreateProcessReply>
    @JvmStatic
    get() = ProcessManagerGrpc.getCreateProcessMethod()

  val destroyProcessMethod: MethodDescriptor<DestroyProcessRequest, Empty>
    @JvmStatic
    get() = ProcessManagerGrpc.getDestroyProcessMethod()

  val awaitTerminationMethod: MethodDescriptor<AwaitTerminationRequest, AwaitTerminationReply>
    @JvmStatic
    get() = ProcessManagerGrpc.getAwaitTerminationMethod()

  val writeStreamMethod: MethodDescriptor<WriteStreamRequest, Empty>
    @JvmStatic
    get() = ProcessManagerGrpc.getWriteStreamMethod()

  val readStreamMethod: MethodDescriptor<ReadStreamRequest, DataChunk>
    @JvmStatic
    get() = ProcessManagerGrpc.getReadStreamMethod()

  /**
   * A stub for issuing RPCs to a(n) intellij.process.mediator.rpc.ProcessManager service as
   * suspending coroutines.
   */
  @StubFor(ProcessManagerGrpc::class)
  class ProcessManagerCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT
  ) : AbstractCoroutineStub<ProcessManagerCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): ProcessManagerCoroutineStub =
        ProcessManagerCoroutineStub(channel, callOptions)

    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    fun openHandle(request: Empty): Flow<OpenHandleReply> = serverStreamingRpc(
      channel,
      ProcessManagerGrpc.getOpenHandleMethod(),
      request,
      callOptions,
      Metadata()
    )
    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return The single response from the server.
     */
    suspend fun createProcess(request: CreateProcessRequest): CreateProcessReply = unaryRpc(
      channel,
      ProcessManagerGrpc.getCreateProcessMethod(),
      request,
      callOptions,
      Metadata()
    )
    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return The single response from the server.
     */
    suspend fun destroyProcess(request: DestroyProcessRequest): Empty = unaryRpc(
      channel,
      ProcessManagerGrpc.getDestroyProcessMethod(),
      request,
      callOptions,
      Metadata()
    )
    /**
     * Executes this RPC and returns the response message, suspending until the RPC completes
     * with [`Status.OK`][Status].  If the RPC completes with another status, a corresponding
     * [StatusException] is thrown.  If this coroutine is cancelled, the RPC is also cancelled
     * with the corresponding exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return The single response from the server.
     */
    suspend fun awaitTermination(request: AwaitTerminationRequest): AwaitTerminationReply =
        unaryRpc(
      channel,
      ProcessManagerGrpc.getAwaitTerminationMethod(),
      request,
      callOptions,
      Metadata()
    )
    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][Status], and fails by throwing a [StatusException] otherwise.  If
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
     * @return A flow that, when collected, emits the responses from the server.
     */
    fun writeStream(requests: Flow<WriteStreamRequest>): Flow<Empty> = bidiStreamingRpc(
      channel,
      ProcessManagerGrpc.getWriteStreamMethod(),
      requests,
      callOptions,
      Metadata()
    )
    /**
     * Returns a [Flow] that, when collected, executes this RPC and emits responses from the
     * server as they arrive.  That flow finishes normally if the server closes its response with
     * [`Status.OK`][Status], and fails by throwing a [StatusException] otherwise.  If
     * collecting the flow downstream fails exceptionally (including via cancellation), the RPC
     * is cancelled with that exception as a cause.
     *
     * @param request The request message to send to the server.
     *
     * @return A flow that, when collected, emits the responses from the server.
     */
    fun readStream(request: ReadStreamRequest): Flow<DataChunk> = serverStreamingRpc(
      channel,
      ProcessManagerGrpc.getReadStreamMethod(),
      request,
      callOptions,
      Metadata()
    )}

  /**
   * Skeletal implementation of the intellij.process.mediator.rpc.ProcessManager service based on
   * Kotlin coroutines.
   */
  abstract class ProcessManagerCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.rpc.ProcessManager.OpenHandle.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open fun openHandle(request: Empty): Flow<OpenHandleReply> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.ProcessManager.OpenHandle is unimplemented"))

    /**
     * Returns the response to an RPC for
     * intellij.process.mediator.rpc.ProcessManager.CreateProcess.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open suspend fun createProcess(request: CreateProcessRequest): CreateProcessReply = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.ProcessManager.CreateProcess is unimplemented"))

    /**
     * Returns the response to an RPC for
     * intellij.process.mediator.rpc.ProcessManager.DestroyProcess.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open suspend fun destroyProcess(request: DestroyProcessRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.ProcessManager.DestroyProcess is unimplemented"))

    /**
     * Returns the response to an RPC for
     * intellij.process.mediator.rpc.ProcessManager.AwaitTermination.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open suspend fun awaitTermination(request: AwaitTerminationRequest): AwaitTerminationReply =
        throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.ProcessManager.AwaitTermination is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.rpc.ProcessManager.WriteStream.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [Status].  If it fails with a
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
    open fun writeStream(requests: Flow<WriteStreamRequest>): Flow<Empty> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.ProcessManager.WriteStream is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for
     * intellij.process.mediator.rpc.ProcessManager.ReadStream.
     *
     * If creating or collecting the returned flow fails with a [StatusException], the RPC
     * will fail with the corresponding [Status].  If it fails with a
     * [java.util.concurrent.CancellationException], the RPC will fail with status
     * `Status.CANCELLED`.  If creating
     * or collecting the returned flow fails for any other reason, the RPC will fail with
     * `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open fun readStream(request: ReadStreamRequest): Flow<DataChunk> = throw
        StatusException(UNIMPLEMENTED.withDescription("Method intellij.process.mediator.rpc.ProcessManager.ReadStream is unimplemented"))

    final override fun bindService(): ServerServiceDefinition =
        builder(ProcessManagerGrpc.getServiceDescriptor())
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
