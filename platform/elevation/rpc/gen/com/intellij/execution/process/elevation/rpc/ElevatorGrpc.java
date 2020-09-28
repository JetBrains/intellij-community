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
  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.CreateProcessRequest,
      com.intellij.execution.process.elevation.rpc.CreateProcessReply> getCreateProcessMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateProcess",
      requestType = com.intellij.execution.process.elevation.rpc.CreateProcessRequest.class,
      responseType = com.intellij.execution.process.elevation.rpc.CreateProcessReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.CreateProcessRequest,
      com.intellij.execution.process.elevation.rpc.CreateProcessReply> getCreateProcessMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.CreateProcessRequest, com.intellij.execution.process.elevation.rpc.CreateProcessReply> getCreateProcessMethod;
    if ((getCreateProcessMethod = ElevatorGrpc.getCreateProcessMethod) == null) {
      synchronized (ElevatorGrpc.class) {
        if ((getCreateProcessMethod = ElevatorGrpc.getCreateProcessMethod) == null) {
          ElevatorGrpc.getCreateProcessMethod = getCreateProcessMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.elevation.rpc.CreateProcessRequest, com.intellij.execution.process.elevation.rpc.CreateProcessReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateProcess"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.CreateProcessRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.CreateProcessReply.getDefaultInstance()))
              .setSchemaDescriptor(new ElevatorMethodDescriptorSupplier("CreateProcess"))
              .build();
        }
      }
    }
    return getCreateProcessMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest,
      com.intellij.execution.process.elevation.rpc.AwaitTerminationReply> getAwaitTerminationMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AwaitTermination",
      requestType = com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest.class,
      responseType = com.intellij.execution.process.elevation.rpc.AwaitTerminationReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest,
      com.intellij.execution.process.elevation.rpc.AwaitTerminationReply> getAwaitTerminationMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest, com.intellij.execution.process.elevation.rpc.AwaitTerminationReply> getAwaitTerminationMethod;
    if ((getAwaitTerminationMethod = ElevatorGrpc.getAwaitTerminationMethod) == null) {
      synchronized (ElevatorGrpc.class) {
        if ((getAwaitTerminationMethod = ElevatorGrpc.getAwaitTerminationMethod) == null) {
          ElevatorGrpc.getAwaitTerminationMethod = getAwaitTerminationMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest, com.intellij.execution.process.elevation.rpc.AwaitTerminationReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AwaitTermination"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.elevation.rpc.AwaitTerminationReply.getDefaultInstance()))
              .setSchemaDescriptor(new ElevatorMethodDescriptorSupplier("AwaitTermination"))
              .build();
        }
      }
    }
    return getAwaitTerminationMethod;
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
    public void createProcess(com.intellij.execution.process.elevation.rpc.CreateProcessRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.CreateProcessReply> responseObserver) {
      asyncUnimplementedUnaryCall(getCreateProcessMethod(), responseObserver);
    }

    /**
     */
    public void awaitTermination(com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.AwaitTerminationReply> responseObserver) {
      asyncUnimplementedUnaryCall(getAwaitTerminationMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getCreateProcessMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.intellij.execution.process.elevation.rpc.CreateProcessRequest,
                com.intellij.execution.process.elevation.rpc.CreateProcessReply>(
                  this, METHODID_CREATE_PROCESS)))
          .addMethod(
            getAwaitTerminationMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest,
                com.intellij.execution.process.elevation.rpc.AwaitTerminationReply>(
                  this, METHODID_AWAIT_TERMINATION)))
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
    public void createProcess(com.intellij.execution.process.elevation.rpc.CreateProcessRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.CreateProcessReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getCreateProcessMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void awaitTermination(com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.AwaitTerminationReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getAwaitTerminationMethod(), getCallOptions()), request, responseObserver);
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
    public com.intellij.execution.process.elevation.rpc.CreateProcessReply createProcess(com.intellij.execution.process.elevation.rpc.CreateProcessRequest request) {
      return blockingUnaryCall(
          getChannel(), getCreateProcessMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.intellij.execution.process.elevation.rpc.AwaitTerminationReply awaitTermination(com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest request) {
      return blockingUnaryCall(
          getChannel(), getAwaitTerminationMethod(), getCallOptions(), request);
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
    public com.google.common.util.concurrent.ListenableFuture<com.intellij.execution.process.elevation.rpc.CreateProcessReply> createProcess(
        com.intellij.execution.process.elevation.rpc.CreateProcessRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getCreateProcessMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.intellij.execution.process.elevation.rpc.AwaitTerminationReply> awaitTermination(
        com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getAwaitTerminationMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CREATE_PROCESS = 0;
  private static final int METHODID_AWAIT_TERMINATION = 1;

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
        case METHODID_CREATE_PROCESS:
          serviceImpl.createProcess((com.intellij.execution.process.elevation.rpc.CreateProcessRequest) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.CreateProcessReply>) responseObserver);
          break;
        case METHODID_AWAIT_TERMINATION:
          serviceImpl.awaitTermination((com.intellij.execution.process.elevation.rpc.AwaitTerminationRequest) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.elevation.rpc.AwaitTerminationReply>) responseObserver);
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
              .addMethod(getCreateProcessMethod())
              .addMethod(getAwaitTerminationMethod())
              .build();
        }
      }
    }
    return result;
  }
}
