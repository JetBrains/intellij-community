package com.intellij.execution.process.elevation.rpc

import com.google.protobuf.Empty
import com.intellij.execution.process.elevation.rpc.ElevatorGrpc.getServiceDescriptor
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
import io.grpc.kotlin.ClientCalls.clientStreamingRpc
import io.grpc.kotlin.ClientCalls.serverStreamingRpc
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls
import io.grpc.kotlin.ServerCalls.clientStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.serverStreamingServerMethodDefinition
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlinx.coroutines.flow.Flow

/**
 * Holder for Kotlin coroutine-based client and server APIs for elevation.rpc.Elevator.
 */
object ElevatorGrpcKt {
  @JvmStatic
  val serviceDescriptor: ServiceDescriptor
    get() = ElevatorGrpc.getServiceDescriptor()

  val createProcessMethod: MethodDescriptor<CreateProcessRequest, CreateProcessReply>
    @JvmStatic
    get() = ElevatorGrpc.getCreateProcessMethod()

  val destroyProcessMethod: MethodDescriptor<DestroyProcessRequest, Empty>
    @JvmStatic
    get() = ElevatorGrpc.getDestroyProcessMethod()

  val awaitTerminationMethod: MethodDescriptor<AwaitTerminationRequest, AwaitTerminationReply>
    @JvmStatic
    get() = ElevatorGrpc.getAwaitTerminationMethod()

  val writeStreamMethod: MethodDescriptor<WriteStreamRequest, Empty>
    @JvmStatic
    get() = ElevatorGrpc.getWriteStreamMethod()

  val readStreamMethod: MethodDescriptor<ReadStreamRequest, DataChunk>
    @JvmStatic
    get() = ElevatorGrpc.getReadStreamMethod()

  val releaseMethod: MethodDescriptor<ReleaseRequest, Empty>
    @JvmStatic
    get() = ElevatorGrpc.getReleaseMethod()

  /**
   * A stub for issuing RPCs to a(n) elevation.rpc.Elevator service as suspending coroutines.
   */
  @StubFor(ElevatorGrpc::class)
  class ElevatorCoroutineStub @JvmOverloads constructor(
    channel: Channel,
    callOptions: CallOptions = DEFAULT
  ) : AbstractCoroutineStub<ElevatorCoroutineStub>(channel, callOptions) {
    override fun build(channel: Channel, callOptions: CallOptions): ElevatorCoroutineStub =
        ElevatorCoroutineStub(channel, callOptions)

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
      ElevatorGrpc.getCreateProcessMethod(),
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
      ElevatorGrpc.getDestroyProcessMethod(),
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
      ElevatorGrpc.getAwaitTerminationMethod(),
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
     * This function collects the [Flow] of requests.  If the server terminates the RPC
     * for any reason before collection of requests is complete, the collection of requests
     * will be cancelled.  If the collection of requests completes exceptionally for any other
     * reason, the RPC will be cancelled for that reason and this method will throw that
     * exception.
     *
     * @param requests A [Flow] of request messages.
     *
     * @return The single response from the server.
     */
    suspend fun writeStream(requests: Flow<WriteStreamRequest>): Empty = clientStreamingRpc(
      channel,
      ElevatorGrpc.getWriteStreamMethod(),
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
      ElevatorGrpc.getReadStreamMethod(),
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
    suspend fun release(request: ReleaseRequest): Empty = unaryRpc(
      channel,
      ElevatorGrpc.getReleaseMethod(),
      request,
      callOptions,
      Metadata()
    )}

  /**
   * Skeletal implementation of the elevation.rpc.Elevator service based on Kotlin coroutines.
   */
  abstract class ElevatorCoroutineImplBase(
    coroutineContext: CoroutineContext = EmptyCoroutineContext
  ) : AbstractCoroutineServerImpl(coroutineContext) {
    /**
     * Returns the response to an RPC for elevation.rpc.Elevator.CreateProcess.
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
        StatusException(UNIMPLEMENTED.withDescription("Method elevation.rpc.Elevator.CreateProcess is unimplemented"))

    /**
     * Returns the response to an RPC for elevation.rpc.Elevator.DestroyProcess.
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
        StatusException(UNIMPLEMENTED.withDescription("Method elevation.rpc.Elevator.DestroyProcess is unimplemented"))

    /**
     * Returns the response to an RPC for elevation.rpc.Elevator.AwaitTermination.
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
        StatusException(UNIMPLEMENTED.withDescription("Method elevation.rpc.Elevator.AwaitTermination is unimplemented"))

    /**
     * Returns the response to an RPC for elevation.rpc.Elevator.WriteStream.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param requests A [Flow] of requests from the client.  This flow can be
     *        collected only once and throws [java.lang.IllegalStateException] on attempts to
     * collect
     *        it more than once.
     */
    open suspend fun writeStream(requests: Flow<WriteStreamRequest>): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method elevation.rpc.Elevator.WriteStream is unimplemented"))

    /**
     * Returns a [Flow] of responses to an RPC for elevation.rpc.Elevator.ReadStream.
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
        StatusException(UNIMPLEMENTED.withDescription("Method elevation.rpc.Elevator.ReadStream is unimplemented"))

    /**
     * Returns the response to an RPC for elevation.rpc.Elevator.Release.
     *
     * If this method fails with a [StatusException], the RPC will fail with the corresponding
     * [Status].  If this method fails with a [java.util.concurrent.CancellationException], the RPC
     * will fail
     * with status `Status.CANCELLED`.  If this method fails for any other reason, the RPC will
     * fail with `Status.UNKNOWN` with the exception as a cause.
     *
     * @param request The request from the client.
     */
    open suspend fun release(request: ReleaseRequest): Empty = throw
        StatusException(UNIMPLEMENTED.withDescription("Method elevation.rpc.Elevator.Release is unimplemented"))

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getCreateProcessMethod(),
      implementation = ::createProcess
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getDestroyProcessMethod(),
      implementation = ::destroyProcess
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getAwaitTerminationMethod(),
      implementation = ::awaitTermination
    ))
      .addMethod(clientStreamingServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getWriteStreamMethod(),
      implementation = ::writeStream
    ))
      .addMethod(serverStreamingServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getReadStreamMethod(),
      implementation = ::readStream
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getReleaseMethod(),
      implementation = ::release
    )).build()
  }
}
