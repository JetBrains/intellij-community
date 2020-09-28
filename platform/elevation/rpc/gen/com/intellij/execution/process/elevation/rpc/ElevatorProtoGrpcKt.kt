package com.intellij.execution.process.elevation.rpc

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
import io.grpc.kotlin.ClientCalls.unaryRpc
import io.grpc.kotlin.ServerCalls
import io.grpc.kotlin.ServerCalls.unaryServerMethodDefinition
import io.grpc.kotlin.StubFor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

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

  val awaitTerminationMethod: MethodDescriptor<AwaitTerminationRequest, AwaitTerminationReply>
    @JvmStatic
    get() = ElevatorGrpc.getAwaitTerminationMethod()

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
    suspend fun awaitTermination(request: AwaitTerminationRequest): AwaitTerminationReply =
        unaryRpc(
      channel,
      ElevatorGrpc.getAwaitTerminationMethod(),
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

    final override fun bindService(): ServerServiceDefinition = builder(getServiceDescriptor())
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getCreateProcessMethod(),
      implementation = ::createProcess
    ))
      .addMethod(unaryServerMethodDefinition(
      context = this.context,
      descriptor = ElevatorGrpc.getAwaitTerminationMethod(),
      implementation = ::awaitTermination
    )).build()
  }
}
