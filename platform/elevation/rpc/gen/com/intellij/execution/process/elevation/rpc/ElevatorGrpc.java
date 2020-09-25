package com.intellij.execution.process.elevation.rpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.31.1)",
    comments = "Source: elevator.proto")
public final class ElevatorGrpc {

  private ElevatorGrpc() {}

  public static final String SERVICE_NAME = "elevation.rpc.Elevator";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.SpawnRequest,
      com.intellij.execution.process.elevation.rpc.SpawnReply> getSpawnMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Spawn",
      requestType = com.intellij.execution.process.elevation.rpc.SpawnRequest.class,
      responseType = com.intellij.execution.process.elevation.rpc.SpawnReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.SpawnRequest,
      com.intellij.execution.process.elevation.rpc.SpawnReply> getSpawnMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.SpawnRequest, com.intellij.execution.process.elevation.rpc.SpawnReply> getSpawnMethod;
    if ((getSpawnMethod = ElevatorGrpc.getSpawnMethod) == null) {
      synchronized (ElevatorGrpc.class) {
        if ((getSpawnMethod = ElevatorGrpc.getSpawnMethod) == null) {
          ElevatorGrpc.getSpawnMethod = getSpawnMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.elevation.rpc.SpawnRequest, com.intellij.execution.process.elevation.rpc.SpawnReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Spawn"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.SpawnRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.SpawnReply.getDefaultInstance()))
              .setSchemaDescriptor(new ElevatorMethodDescriptorSupplier("Spawn"))
              .build();
        }
      }
    }
    return getSpawnMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.AwaitRequest,
      com.intellij.execution.process.elevation.rpc.AwaitReply> getAwaitMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Await",
      requestType = com.intellij.execution.process.elevation.rpc.AwaitRequest.class,
      responseType = com.intellij.execution.process.elevation.rpc.AwaitReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.AwaitRequest,
      com.intellij.execution.process.elevation.rpc.AwaitReply> getAwaitMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.AwaitRequest, com.intellij.execution.process.elevation.rpc.AwaitReply> getAwaitMethod;
    if ((getAwaitMethod = ElevatorGrpc.getAwaitMethod) == null) {
      synchronized (ElevatorGrpc.class) {
        if ((getAwaitMethod = ElevatorGrpc.getAwaitMethod) == null) {
          ElevatorGrpc.getAwaitMethod = getAwaitMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.elevation.rpc.AwaitRequest, com.intellij.execution.process.elevation.rpc.AwaitReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Await"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.AwaitRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.AwaitReply.getDefaultInstance()))
              .setSchemaDescriptor(new ElevatorMethodDescriptorSupplier("Await"))
              .build();
        }
      }
    }
    return getAwaitMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ElevatorStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ElevatorStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ElevatorStub>() {
        @java.lang.Override
        public ElevatorStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ElevatorStub(channel, callOptions);
        }
      };
    return ElevatorStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ElevatorBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ElevatorBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ElevatorBlockingStub>() {
        @java.lang.Override
        public ElevatorBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ElevatorBlockingStub(channel, callOptions);
        }
      };
    return ElevatorBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ElevatorFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ElevatorFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ElevatorFutureStub>() {
        @java.lang.Override
        public ElevatorFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ElevatorFutureStub(channel, callOptions);
        }
      };
    return ElevatorFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class ElevatorImplBase implements io.grpc.BindableService {

    /**
     */
    public void spawn(com.intellij.execution.process.elevation.rpc.SpawnRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.SpawnReply> responseObserver) {
      asyncUnimplementedUnaryCall(getSpawnMethod(), responseObserver);
    }

    /**
     */
    public void await(com.intellij.execution.process.elevation.rpc.AwaitRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.AwaitReply> responseObserver) {
      asyncUnimplementedUnaryCall(getAwaitMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSpawnMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.intellij.execution.process.elevation.rpc.SpawnRequest,
                com.intellij.execution.process.elevation.rpc.SpawnReply>(
                  this, METHODID_SPAWN)))
          .addMethod(
            getAwaitMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.intellij.execution.process.elevation.rpc.AwaitRequest,
                com.intellij.execution.process.elevation.rpc.AwaitReply>(
                  this, METHODID_AWAIT)))
          .build();
    }
  }

  /**
   */
  public static final class ElevatorStub extends io.grpc.stub.AbstractAsyncStub<ElevatorStub> {
    private ElevatorStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ElevatorStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ElevatorStub(channel, callOptions);
    }

    /**
     */
    public void spawn(com.intellij.execution.process.elevation.rpc.SpawnRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.SpawnReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getSpawnMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void await(com.intellij.execution.process.elevation.rpc.AwaitRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.AwaitReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getAwaitMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class ElevatorBlockingStub extends io.grpc.stub.AbstractBlockingStub<ElevatorBlockingStub> {
    private ElevatorBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ElevatorBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ElevatorBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.intellij.execution.process.elevation.rpc.SpawnReply spawn(com.intellij.execution.process.elevation.rpc.SpawnRequest request) {
      return blockingUnaryCall(
          getChannel(), getSpawnMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.intellij.execution.process.elevation.rpc.AwaitReply await(com.intellij.execution.process.elevation.rpc.AwaitRequest request) {
      return blockingUnaryCall(
          getChannel(), getAwaitMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class ElevatorFutureStub extends io.grpc.stub.AbstractFutureStub<ElevatorFutureStub> {
    private ElevatorFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ElevatorFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ElevatorFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.intellij.execution.process.elevation.rpc.SpawnReply> spawn(
        com.intellij.execution.process.elevation.rpc.SpawnRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getSpawnMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.intellij.execution.process.elevation.rpc.AwaitReply> await(
        com.intellij.execution.process.elevation.rpc.AwaitRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getAwaitMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SPAWN = 0;
  private static final int METHODID_AWAIT = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ElevatorImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ElevatorImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SPAWN:
          serviceImpl.spawn((com.intellij.execution.process.elevation.rpc.SpawnRequest) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.SpawnReply>) responseObserver);
          break;
        case METHODID_AWAIT:
          serviceImpl.await((com.intellij.execution.process.elevation.rpc.AwaitRequest) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.AwaitReply>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class ElevatorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ElevatorBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.intellij.execution.process.elevation.rpc.ElevatorProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Elevator");
    }
  }

  private static final class ElevatorFileDescriptorSupplier
      extends ElevatorBaseDescriptorSupplier {
    ElevatorFileDescriptorSupplier() {}
  }

  private static final class ElevatorMethodDescriptorSupplier
      extends ElevatorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ElevatorMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ElevatorGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ElevatorFileDescriptorSupplier())
              .addMethod(getSpawnMethod())
              .addMethod(getAwaitMethod())
              .build();
        }
      }
    }
    return result;
  }
}
