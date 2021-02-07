package com.intellij.execution.process.mediator.rpc;

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
    comments = "Source: processMediator.proto")
public final class DaemonGrpc {

  private DaemonGrpc() {}

  public static final String SERVICE_NAME = "intellij.process.mediator.rpc.Daemon";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.rpc.QuotaOptions,
      com.google.protobuf.Empty> getAdjustQuotaMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AdjustQuota",
      requestType = com.intellij.execution.process.mediator.rpc.QuotaOptions.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.rpc.QuotaOptions,
      com.google.protobuf.Empty> getAdjustQuotaMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.rpc.QuotaOptions, com.google.protobuf.Empty> getAdjustQuotaMethod;
    if ((getAdjustQuotaMethod = DaemonGrpc.getAdjustQuotaMethod) == null) {
      synchronized (DaemonGrpc.class) {
        if ((getAdjustQuotaMethod = DaemonGrpc.getAdjustQuotaMethod) == null) {
          DaemonGrpc.getAdjustQuotaMethod = getAdjustQuotaMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.mediator.rpc.QuotaOptions, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AdjustQuota"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.rpc.QuotaOptions.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new DaemonMethodDescriptorSupplier("AdjustQuota"))
              .build();
        }
      }
    }
    return getAdjustQuotaMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.google.protobuf.Empty,
      com.intellij.execution.process.mediator.rpc.QuotaState> getListenQuotaStateUpdatesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListenQuotaStateUpdates",
      requestType = com.google.protobuf.Empty.class,
      responseType = com.intellij.execution.process.mediator.rpc.QuotaState.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.google.protobuf.Empty,
      com.intellij.execution.process.mediator.rpc.QuotaState> getListenQuotaStateUpdatesMethod() {
    io.grpc.MethodDescriptor<com.google.protobuf.Empty, com.intellij.execution.process.mediator.rpc.QuotaState> getListenQuotaStateUpdatesMethod;
    if ((getListenQuotaStateUpdatesMethod = DaemonGrpc.getListenQuotaStateUpdatesMethod) == null) {
      synchronized (DaemonGrpc.class) {
        if ((getListenQuotaStateUpdatesMethod = DaemonGrpc.getListenQuotaStateUpdatesMethod) == null) {
          DaemonGrpc.getListenQuotaStateUpdatesMethod = getListenQuotaStateUpdatesMethod =
              io.grpc.MethodDescriptor.<com.google.protobuf.Empty, com.intellij.execution.process.mediator.rpc.QuotaState>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListenQuotaStateUpdates"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.rpc.QuotaState.getDefaultInstance()))
              .setSchemaDescriptor(new DaemonMethodDescriptorSupplier("ListenQuotaStateUpdates"))
              .build();
        }
      }
    }
    return getListenQuotaStateUpdatesMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.google.protobuf.Empty,
      com.google.protobuf.Empty> getShutdownMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Shutdown",
      requestType = com.google.protobuf.Empty.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.google.protobuf.Empty,
      com.google.protobuf.Empty> getShutdownMethod() {
    io.grpc.MethodDescriptor<com.google.protobuf.Empty, com.google.protobuf.Empty> getShutdownMethod;
    if ((getShutdownMethod = DaemonGrpc.getShutdownMethod) == null) {
      synchronized (DaemonGrpc.class) {
        if ((getShutdownMethod = DaemonGrpc.getShutdownMethod) == null) {
          DaemonGrpc.getShutdownMethod = getShutdownMethod =
              io.grpc.MethodDescriptor.<com.google.protobuf.Empty, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Shutdown"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new DaemonMethodDescriptorSupplier("Shutdown"))
              .build();
        }
      }
    }
    return getShutdownMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DaemonStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DaemonStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DaemonStub>() {
        @java.lang.Override
        public DaemonStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DaemonStub(channel, callOptions);
        }
      };
    return DaemonStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DaemonBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DaemonBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DaemonBlockingStub>() {
        @java.lang.Override
        public DaemonBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DaemonBlockingStub(channel, callOptions);
        }
      };
    return DaemonBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DaemonFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DaemonFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DaemonFutureStub>() {
        @java.lang.Override
        public DaemonFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DaemonFutureStub(channel, callOptions);
        }
      };
    return DaemonFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class DaemonImplBase implements io.grpc.BindableService {

    /**
     */
    public void adjustQuota(com.intellij.execution.process.mediator.rpc.QuotaOptions request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      asyncUnimplementedUnaryCall(getAdjustQuotaMethod(), responseObserver);
    }

    /**
     */
    public void listenQuotaStateUpdates(com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.rpc.QuotaState> responseObserver) {
      asyncUnimplementedUnaryCall(getListenQuotaStateUpdatesMethod(), responseObserver);
    }

    /**
     */
    public void shutdown(com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      asyncUnimplementedUnaryCall(getShutdownMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getAdjustQuotaMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.intellij.execution.process.mediator.rpc.QuotaOptions,
                com.google.protobuf.Empty>(
                  this, METHODID_ADJUST_QUOTA)))
          .addMethod(
            getListenQuotaStateUpdatesMethod(),
            asyncServerStreamingCall(
              new MethodHandlers<
                com.google.protobuf.Empty,
                com.intellij.execution.process.mediator.rpc.QuotaState>(
                  this, METHODID_LISTEN_QUOTA_STATE_UPDATES)))
          .addMethod(
            getShutdownMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                com.google.protobuf.Empty,
                com.google.protobuf.Empty>(
                  this, METHODID_SHUTDOWN)))
          .build();
    }
  }

  /**
   */
  public static final class DaemonStub extends io.grpc.stub.AbstractAsyncStub<DaemonStub> {
    private DaemonStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DaemonStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DaemonStub(channel, callOptions);
    }

    /**
     */
    public void adjustQuota(com.intellij.execution.process.mediator.rpc.QuotaOptions request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getAdjustQuotaMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listenQuotaStateUpdates(com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.rpc.QuotaState> responseObserver) {
      asyncServerStreamingCall(
          getChannel().newCall(getListenQuotaStateUpdatesMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void shutdown(com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getShutdownMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class DaemonBlockingStub extends io.grpc.stub.AbstractBlockingStub<DaemonBlockingStub> {
    private DaemonBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DaemonBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DaemonBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.google.protobuf.Empty adjustQuota(com.intellij.execution.process.mediator.rpc.QuotaOptions request) {
      return blockingUnaryCall(
          getChannel(), getAdjustQuotaMethod(), getCallOptions(), request);
    }

    /**
     */
    public java.util.Iterator<com.intellij.execution.process.mediator.rpc.QuotaState> listenQuotaStateUpdates(
        com.google.protobuf.Empty request) {
      return blockingServerStreamingCall(
          getChannel(), getListenQuotaStateUpdatesMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.google.protobuf.Empty shutdown(com.google.protobuf.Empty request) {
      return blockingUnaryCall(
          getChannel(), getShutdownMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class DaemonFutureStub extends io.grpc.stub.AbstractFutureStub<DaemonFutureStub> {
    private DaemonFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DaemonFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DaemonFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> adjustQuota(
        com.intellij.execution.process.mediator.rpc.QuotaOptions request) {
      return futureUnaryCall(
          getChannel().newCall(getAdjustQuotaMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> shutdown(
        com.google.protobuf.Empty request) {
      return futureUnaryCall(
          getChannel().newCall(getShutdownMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_ADJUST_QUOTA = 0;
  private static final int METHODID_LISTEN_QUOTA_STATE_UPDATES = 1;
  private static final int METHODID_SHUTDOWN = 2;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final DaemonImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(DaemonImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_ADJUST_QUOTA:
          serviceImpl.adjustQuota((com.intellij.execution.process.mediator.rpc.QuotaOptions) request,
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
          break;
        case METHODID_LISTEN_QUOTA_STATE_UPDATES:
          serviceImpl.listenQuotaStateUpdates((com.google.protobuf.Empty) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.rpc.QuotaState>) responseObserver);
          break;
        case METHODID_SHUTDOWN:
          serviceImpl.shutdown((com.google.protobuf.Empty) request,
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
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

  private static abstract class DaemonBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DaemonBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.intellij.execution.process.mediator.rpc.ProcessMediatorProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Daemon");
    }
  }

  private static final class DaemonFileDescriptorSupplier
      extends DaemonBaseDescriptorSupplier {
    DaemonFileDescriptorSupplier() {}
  }

  private static final class DaemonMethodDescriptorSupplier
      extends DaemonBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    DaemonMethodDescriptorSupplier(String methodName) {
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
      synchronized (DaemonGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DaemonFileDescriptorSupplier())
              .addMethod(getAdjustQuotaMethod())
              .addMethod(getListenQuotaStateUpdatesMethod())
              .addMethod(getShutdownMethod())
              .build();
        }
      }
    }
    return result;
  }
}
