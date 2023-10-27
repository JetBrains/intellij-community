package com.intellij.execution.process.mediator.common.rpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.57.2)",
    comments = "Source: processMediator.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ProcessManagerGrpc {

  private ProcessManagerGrpc() {}

  public static final java.lang.String SERVICE_NAME = "intellij.process.mediator.common.rpc.ProcessManager";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.google.protobuf.Empty,
      com.intellij.execution.process.mediator.common.rpc.OpenHandleReply> getOpenHandleMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "OpenHandle",
      requestType = com.google.protobuf.Empty.class,
      responseType = com.intellij.execution.process.mediator.common.rpc.OpenHandleReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.google.protobuf.Empty,
      com.intellij.execution.process.mediator.common.rpc.OpenHandleReply> getOpenHandleMethod() {
    io.grpc.MethodDescriptor<com.google.protobuf.Empty, com.intellij.execution.process.mediator.common.rpc.OpenHandleReply> getOpenHandleMethod;
    if ((getOpenHandleMethod = ProcessManagerGrpc.getOpenHandleMethod) == null) {
      synchronized (ProcessManagerGrpc.class) {
        if ((getOpenHandleMethod = ProcessManagerGrpc.getOpenHandleMethod) == null) {
          ProcessManagerGrpc.getOpenHandleMethod = getOpenHandleMethod =
              io.grpc.MethodDescriptor.<com.google.protobuf.Empty, com.intellij.execution.process.mediator.common.rpc.OpenHandleReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "OpenHandle"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.OpenHandleReply.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessManagerMethodDescriptorSupplier("OpenHandle"))
              .build();
        }
      }
    }
    return getOpenHandleMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest,
      com.intellij.execution.process.mediator.common.rpc.CreateProcessReply> getCreateProcessMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CreateProcess",
      requestType = com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest.class,
      responseType = com.intellij.execution.process.mediator.common.rpc.CreateProcessReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest,
      com.intellij.execution.process.mediator.common.rpc.CreateProcessReply> getCreateProcessMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest, com.intellij.execution.process.mediator.common.rpc.CreateProcessReply> getCreateProcessMethod;
    if ((getCreateProcessMethod = ProcessManagerGrpc.getCreateProcessMethod) == null) {
      synchronized (ProcessManagerGrpc.class) {
        if ((getCreateProcessMethod = ProcessManagerGrpc.getCreateProcessMethod) == null) {
          ProcessManagerGrpc.getCreateProcessMethod = getCreateProcessMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest, com.intellij.execution.process.mediator.common.rpc.CreateProcessReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CreateProcess"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.CreateProcessReply.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessManagerMethodDescriptorSupplier("CreateProcess"))
              .build();
        }
      }
    }
    return getCreateProcessMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest,
      com.google.protobuf.Empty> getDestroyProcessMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DestroyProcess",
      requestType = com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest,
      com.google.protobuf.Empty> getDestroyProcessMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest, com.google.protobuf.Empty> getDestroyProcessMethod;
    if ((getDestroyProcessMethod = ProcessManagerGrpc.getDestroyProcessMethod) == null) {
      synchronized (ProcessManagerGrpc.class) {
        if ((getDestroyProcessMethod = ProcessManagerGrpc.getDestroyProcessMethod) == null) {
          ProcessManagerGrpc.getDestroyProcessMethod = getDestroyProcessMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DestroyProcess"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessManagerMethodDescriptorSupplier("DestroyProcess"))
              .build();
        }
      }
    }
    return getDestroyProcessMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest,
      com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply> getAwaitTerminationMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "AwaitTermination",
      requestType = com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest.class,
      responseType = com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest,
      com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply> getAwaitTerminationMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest, com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply> getAwaitTerminationMethod;
    if ((getAwaitTerminationMethod = ProcessManagerGrpc.getAwaitTerminationMethod) == null) {
      synchronized (ProcessManagerGrpc.class) {
        if ((getAwaitTerminationMethod = ProcessManagerGrpc.getAwaitTerminationMethod) == null) {
          ProcessManagerGrpc.getAwaitTerminationMethod = getAwaitTerminationMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest, com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "AwaitTermination"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessManagerMethodDescriptorSupplier("AwaitTermination"))
              .build();
        }
      }
    }
    return getAwaitTerminationMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest,
      com.google.protobuf.Empty> getWriteStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "WriteStream",
      requestType = com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest.class,
      responseType = com.google.protobuf.Empty.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest,
      com.google.protobuf.Empty> getWriteStreamMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest, com.google.protobuf.Empty> getWriteStreamMethod;
    if ((getWriteStreamMethod = ProcessManagerGrpc.getWriteStreamMethod) == null) {
      synchronized (ProcessManagerGrpc.class) {
        if ((getWriteStreamMethod = ProcessManagerGrpc.getWriteStreamMethod) == null) {
          ProcessManagerGrpc.getWriteStreamMethod = getWriteStreamMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest, com.google.protobuf.Empty>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "WriteStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.google.protobuf.Empty.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessManagerMethodDescriptorSupplier("WriteStream"))
              .build();
        }
      }
    }
    return getWriteStreamMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest,
      com.intellij.execution.process.mediator.common.rpc.DataChunk> getReadStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ReadStream",
      requestType = com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest.class,
      responseType = com.intellij.execution.process.mediator.common.rpc.DataChunk.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest,
      com.intellij.execution.process.mediator.common.rpc.DataChunk> getReadStreamMethod() {
    io.grpc.MethodDescriptor<com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest, com.intellij.execution.process.mediator.common.rpc.DataChunk> getReadStreamMethod;
    if ((getReadStreamMethod = ProcessManagerGrpc.getReadStreamMethod) == null) {
      synchronized (ProcessManagerGrpc.class) {
        if ((getReadStreamMethod = ProcessManagerGrpc.getReadStreamMethod) == null) {
          ProcessManagerGrpc.getReadStreamMethod = getReadStreamMethod =
              io.grpc.MethodDescriptor.<com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest, com.intellij.execution.process.mediator.common.rpc.DataChunk>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ReadStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.intellij.execution.process.mediator.common.rpc.DataChunk.getDefaultInstance()))
              .setSchemaDescriptor(new ProcessManagerMethodDescriptorSupplier("ReadStream"))
              .build();
        }
      }
    }
    return getReadStreamMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ProcessManagerStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProcessManagerStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProcessManagerStub>() {
        @java.lang.Override
        public ProcessManagerStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProcessManagerStub(channel, callOptions);
        }
      };
    return ProcessManagerStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ProcessManagerBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProcessManagerBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProcessManagerBlockingStub>() {
        @java.lang.Override
        public ProcessManagerBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProcessManagerBlockingStub(channel, callOptions);
        }
      };
    return ProcessManagerBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ProcessManagerFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ProcessManagerFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ProcessManagerFutureStub>() {
        @java.lang.Override
        public ProcessManagerFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ProcessManagerFutureStub(channel, callOptions);
        }
      };
    return ProcessManagerFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * The resulting server stream emit a single element on start, and doesn't end until the client closes the RPC,
     * which defines the lifetime of the handle.
     * </pre>
     */
    default void openHandle(com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.OpenHandleReply> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getOpenHandleMethod(), responseObserver);
    }

    /**
     */
    default void createProcess(com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.CreateProcessReply> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCreateProcessMethod(), responseObserver);
    }

    /**
     */
    default void destroyProcess(com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDestroyProcessMethod(), responseObserver);
    }

    /**
     */
    default void awaitTermination(com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getAwaitTerminationMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest> writeStream(
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getWriteStreamMethod(), responseObserver);
    }

    /**
     */
    default void readStream(com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.DataChunk> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReadStreamMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ProcessManager.
   */
  public static abstract class ProcessManagerImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ProcessManagerGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ProcessManager.
   */
  public static final class ProcessManagerStub
      extends io.grpc.stub.AbstractAsyncStub<ProcessManagerStub> {
    private ProcessManagerStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProcessManagerStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProcessManagerStub(channel, callOptions);
    }

    /**
     * <pre>
     * The resulting server stream emit a single element on start, and doesn't end until the client closes the RPC,
     * which defines the lifetime of the handle.
     * </pre>
     */
    public void openHandle(com.google.protobuf.Empty request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.OpenHandleReply> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getOpenHandleMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void createProcess(com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.CreateProcessReply> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCreateProcessMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void destroyProcess(com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest request,
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDestroyProcessMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void awaitTermination(com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getAwaitTerminationMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest> writeStream(
        io.grpc.stub.StreamObserver<com.google.protobuf.Empty> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getWriteStreamMethod(), getCallOptions()), responseObserver);
    }

    /**
     */
    public void readStream(com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest request,
        io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.DataChunk> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getReadStreamMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ProcessManager.
   */
  public static final class ProcessManagerBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ProcessManagerBlockingStub> {
    private ProcessManagerBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProcessManagerBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProcessManagerBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * The resulting server stream emit a single element on start, and doesn't end until the client closes the RPC,
     * which defines the lifetime of the handle.
     * </pre>
     */
    public java.util.Iterator<com.intellij.execution.process.mediator.common.rpc.OpenHandleReply> openHandle(
        com.google.protobuf.Empty request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getOpenHandleMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.intellij.execution.process.mediator.common.rpc.CreateProcessReply createProcess(com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCreateProcessMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.google.protobuf.Empty destroyProcess(com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDestroyProcessMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply awaitTermination(com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getAwaitTerminationMethod(), getCallOptions(), request);
    }

    /**
     */
    public java.util.Iterator<com.intellij.execution.process.mediator.common.rpc.DataChunk> readStream(
        com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getReadStreamMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ProcessManager.
   */
  public static final class ProcessManagerFutureStub
      extends io.grpc.stub.AbstractFutureStub<ProcessManagerFutureStub> {
    private ProcessManagerFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ProcessManagerFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ProcessManagerFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.intellij.execution.process.mediator.common.rpc.CreateProcessReply> createProcess(
        com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCreateProcessMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.google.protobuf.Empty> destroyProcess(
        com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDestroyProcessMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply> awaitTermination(
        com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getAwaitTerminationMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_OPEN_HANDLE = 0;
  private static final int METHODID_CREATE_PROCESS = 1;
  private static final int METHODID_DESTROY_PROCESS = 2;
  private static final int METHODID_AWAIT_TERMINATION = 3;
  private static final int METHODID_READ_STREAM = 4;
  private static final int METHODID_WRITE_STREAM = 5;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_OPEN_HANDLE:
          serviceImpl.openHandle((com.google.protobuf.Empty) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.OpenHandleReply>) responseObserver);
          break;
        case METHODID_CREATE_PROCESS:
          serviceImpl.createProcess((com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.CreateProcessReply>) responseObserver);
          break;
        case METHODID_DESTROY_PROCESS:
          serviceImpl.destroyProcess((com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest) request,
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
          break;
        case METHODID_AWAIT_TERMINATION:
          serviceImpl.awaitTermination((com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply>) responseObserver);
          break;
        case METHODID_READ_STREAM:
          serviceImpl.readStream((com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest) request,
              (io.grpc.stub.StreamObserver<com.intellij.execution.process.mediator.common.rpc.DataChunk>) responseObserver);
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
        case METHODID_WRITE_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.writeStream(
              (io.grpc.stub.StreamObserver<com.google.protobuf.Empty>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getOpenHandleMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.google.protobuf.Empty,
              com.intellij.execution.process.mediator.common.rpc.OpenHandleReply>(
                service, METHODID_OPEN_HANDLE)))
        .addMethod(
          getCreateProcessMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.intellij.execution.process.mediator.common.rpc.CreateProcessRequest,
              com.intellij.execution.process.mediator.common.rpc.CreateProcessReply>(
                service, METHODID_CREATE_PROCESS)))
        .addMethod(
          getDestroyProcessMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.intellij.execution.process.mediator.common.rpc.DestroyProcessRequest,
              com.google.protobuf.Empty>(
                service, METHODID_DESTROY_PROCESS)))
        .addMethod(
          getAwaitTerminationMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.intellij.execution.process.mediator.common.rpc.AwaitTerminationRequest,
              com.intellij.execution.process.mediator.common.rpc.AwaitTerminationReply>(
                service, METHODID_AWAIT_TERMINATION)))
        .addMethod(
          getWriteStreamMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.intellij.execution.process.mediator.common.rpc.WriteStreamRequest,
              com.google.protobuf.Empty>(
                service, METHODID_WRITE_STREAM)))
        .addMethod(
          getReadStreamMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.intellij.execution.process.mediator.common.rpc.ReadStreamRequest,
              com.intellij.execution.process.mediator.common.rpc.DataChunk>(
                service, METHODID_READ_STREAM)))
        .build();
  }

  private static abstract class ProcessManagerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ProcessManagerBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.intellij.execution.process.mediator.common.rpc.ProcessMediatorProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ProcessManager");
    }
  }

  private static final class ProcessManagerFileDescriptorSupplier
      extends ProcessManagerBaseDescriptorSupplier {
    ProcessManagerFileDescriptorSupplier() {}
  }

  private static final class ProcessManagerMethodDescriptorSupplier
      extends ProcessManagerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ProcessManagerMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ProcessManagerGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ProcessManagerFileDescriptorSupplier())
              .addMethod(getOpenHandleMethod())
              .addMethod(getCreateProcessMethod())
              .addMethod(getDestroyProcessMethod())
              .addMethod(getAwaitTerminationMethod())
              .addMethod(getWriteStreamMethod())
              .addMethod(getReadStreamMethod())
              .build();
        }
      }
    }
    return result;
  }
}
