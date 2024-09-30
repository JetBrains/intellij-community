package org.jetbrains.embeddings.local.server.stubs;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.53.0)",
    comments = "Source: embeddings.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class embedding_serviceGrpc {

  private embedding_serviceGrpc() {}

  public static final String SERVICE_NAME = "org.jetbrains.embeddings.local.server.stubs.embedding_service";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<Embeddings.present_request,
      Embeddings.present_response> getEnsureVectorsPresentMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ensure_vectors_present",
      requestType = Embeddings.present_request.class,
      responseType = Embeddings.present_response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<Embeddings.present_request,
      Embeddings.present_response> getEnsureVectorsPresentMethod() {
    io.grpc.MethodDescriptor<Embeddings.present_request, Embeddings.present_response> getEnsureVectorsPresentMethod;
    if ((getEnsureVectorsPresentMethod = embedding_serviceGrpc.getEnsureVectorsPresentMethod) == null) {
      synchronized (embedding_serviceGrpc.class) {
        if ((getEnsureVectorsPresentMethod = embedding_serviceGrpc.getEnsureVectorsPresentMethod) == null) {
          embedding_serviceGrpc.getEnsureVectorsPresentMethod = getEnsureVectorsPresentMethod =
              io.grpc.MethodDescriptor.<Embeddings.present_request, Embeddings.present_response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ensure_vectors_present"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.present_request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.present_response.getDefaultInstance()))
              .build();
        }
      }
    }
    return getEnsureVectorsPresentMethod;
  }

  private static volatile io.grpc.MethodDescriptor<Embeddings.remove_request,
      Embeddings.remove_response> getRemoveVectorsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "remove_vectors",
      requestType = Embeddings.remove_request.class,
      responseType = Embeddings.remove_response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<Embeddings.remove_request,
      Embeddings.remove_response> getRemoveVectorsMethod() {
    io.grpc.MethodDescriptor<Embeddings.remove_request, Embeddings.remove_response> getRemoveVectorsMethod;
    if ((getRemoveVectorsMethod = embedding_serviceGrpc.getRemoveVectorsMethod) == null) {
      synchronized (embedding_serviceGrpc.class) {
        if ((getRemoveVectorsMethod = embedding_serviceGrpc.getRemoveVectorsMethod) == null) {
          embedding_serviceGrpc.getRemoveVectorsMethod = getRemoveVectorsMethod =
              io.grpc.MethodDescriptor.<Embeddings.remove_request, Embeddings.remove_response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "remove_vectors"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.remove_request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.remove_response.getDefaultInstance()))
              .build();
        }
      }
    }
    return getRemoveVectorsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<Embeddings.search_request,
      Embeddings.search_response> getSearchMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "search",
      requestType = Embeddings.search_request.class,
      responseType = Embeddings.search_response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<Embeddings.search_request,
      Embeddings.search_response> getSearchMethod() {
    io.grpc.MethodDescriptor<Embeddings.search_request, Embeddings.search_response> getSearchMethod;
    if ((getSearchMethod = embedding_serviceGrpc.getSearchMethod) == null) {
      synchronized (embedding_serviceGrpc.class) {
        if ((getSearchMethod = embedding_serviceGrpc.getSearchMethod) == null) {
          embedding_serviceGrpc.getSearchMethod = getSearchMethod =
              io.grpc.MethodDescriptor.<Embeddings.search_request, Embeddings.search_response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "search"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.search_request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.search_response.getDefaultInstance()))
              .build();
        }
      }
    }
    return getSearchMethod;
  }

  private static volatile io.grpc.MethodDescriptor<Embeddings.clear_request,
      Embeddings.clear_response> getClearStorageMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "clear_storage",
      requestType = Embeddings.clear_request.class,
      responseType = Embeddings.clear_response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<Embeddings.clear_request,
      Embeddings.clear_response> getClearStorageMethod() {
    io.grpc.MethodDescriptor<Embeddings.clear_request, Embeddings.clear_response> getClearStorageMethod;
    if ((getClearStorageMethod = embedding_serviceGrpc.getClearStorageMethod) == null) {
      synchronized (embedding_serviceGrpc.class) {
        if ((getClearStorageMethod = embedding_serviceGrpc.getClearStorageMethod) == null) {
          embedding_serviceGrpc.getClearStorageMethod = getClearStorageMethod =
              io.grpc.MethodDescriptor.<Embeddings.clear_request, Embeddings.clear_response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "clear_storage"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.clear_request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.clear_response.getDefaultInstance()))
              .build();
        }
      }
    }
    return getClearStorageMethod;
  }

  private static volatile io.grpc.MethodDescriptor<Embeddings.start_request,
      Embeddings.start_response> getStartIndexingSessionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "start_indexing_session",
      requestType = Embeddings.start_request.class,
      responseType = Embeddings.start_response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<Embeddings.start_request,
      Embeddings.start_response> getStartIndexingSessionMethod() {
    io.grpc.MethodDescriptor<Embeddings.start_request, Embeddings.start_response> getStartIndexingSessionMethod;
    if ((getStartIndexingSessionMethod = embedding_serviceGrpc.getStartIndexingSessionMethod) == null) {
      synchronized (embedding_serviceGrpc.class) {
        if ((getStartIndexingSessionMethod = embedding_serviceGrpc.getStartIndexingSessionMethod) == null) {
          embedding_serviceGrpc.getStartIndexingSessionMethod = getStartIndexingSessionMethod =
              io.grpc.MethodDescriptor.<Embeddings.start_request, Embeddings.start_response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "start_indexing_session"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.start_request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.start_response.getDefaultInstance()))
              .build();
        }
      }
    }
    return getStartIndexingSessionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<Embeddings.finish_request,
      Embeddings.finish_response> getFinishIndexingSessionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "finish_indexing_session",
      requestType = Embeddings.finish_request.class,
      responseType = Embeddings.finish_response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<Embeddings.finish_request,
      Embeddings.finish_response> getFinishIndexingSessionMethod() {
    io.grpc.MethodDescriptor<Embeddings.finish_request, Embeddings.finish_response> getFinishIndexingSessionMethod;
    if ((getFinishIndexingSessionMethod = embedding_serviceGrpc.getFinishIndexingSessionMethod) == null) {
      synchronized (embedding_serviceGrpc.class) {
        if ((getFinishIndexingSessionMethod = embedding_serviceGrpc.getFinishIndexingSessionMethod) == null) {
          embedding_serviceGrpc.getFinishIndexingSessionMethod = getFinishIndexingSessionMethod =
              io.grpc.MethodDescriptor.<Embeddings.finish_request, Embeddings.finish_response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "finish_indexing_session"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.finish_request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.finish_response.getDefaultInstance()))
              .build();
        }
      }
    }
    return getFinishIndexingSessionMethod;
  }

  private static volatile io.grpc.MethodDescriptor<Embeddings.stats_request,
      Embeddings.stats_response> getGetStorageStatsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "get_storage_stats",
      requestType = Embeddings.stats_request.class,
      responseType = Embeddings.stats_response.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<Embeddings.stats_request,
      Embeddings.stats_response> getGetStorageStatsMethod() {
    io.grpc.MethodDescriptor<Embeddings.stats_request, Embeddings.stats_response> getGetStorageStatsMethod;
    if ((getGetStorageStatsMethod = embedding_serviceGrpc.getGetStorageStatsMethod) == null) {
      synchronized (embedding_serviceGrpc.class) {
        if ((getGetStorageStatsMethod = embedding_serviceGrpc.getGetStorageStatsMethod) == null) {
          embedding_serviceGrpc.getGetStorageStatsMethod = getGetStorageStatsMethod =
              io.grpc.MethodDescriptor.<Embeddings.stats_request, Embeddings.stats_response>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "get_storage_stats"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.stats_request.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.lite.ProtoLiteUtils.marshaller(
                  Embeddings.stats_response.getDefaultInstance()))
              .build();
        }
      }
    }
    return getGetStorageStatsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static embedding_serviceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<embedding_serviceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<embedding_serviceStub>() {
        @Override
        public embedding_serviceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new embedding_serviceStub(channel, callOptions);
        }
      };
    return embedding_serviceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static embedding_serviceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<embedding_serviceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<embedding_serviceBlockingStub>() {
        @Override
        public embedding_serviceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new embedding_serviceBlockingStub(channel, callOptions);
        }
      };
    return embedding_serviceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static embedding_serviceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<embedding_serviceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<embedding_serviceFutureStub>() {
        @Override
        public embedding_serviceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new embedding_serviceFutureStub(channel, callOptions);
        }
      };
    return embedding_serviceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class embedding_serviceImplBase implements io.grpc.BindableService {

    /**
     */
    public void ensureVectorsPresent(Embeddings.present_request request,
                                     io.grpc.stub.StreamObserver<Embeddings.present_response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getEnsureVectorsPresentMethod(), responseObserver);
    }

    /**
     */
    public void removeVectors(Embeddings.remove_request request,
                              io.grpc.stub.StreamObserver<Embeddings.remove_response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRemoveVectorsMethod(), responseObserver);
    }

    /**
     */
    public void search(Embeddings.search_request request,
                       io.grpc.stub.StreamObserver<Embeddings.search_response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSearchMethod(), responseObserver);
    }

    /**
     */
    public void clearStorage(Embeddings.clear_request request,
                             io.grpc.stub.StreamObserver<Embeddings.clear_response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getClearStorageMethod(), responseObserver);
    }

    /**
     */
    public void startIndexingSession(Embeddings.start_request request,
                                     io.grpc.stub.StreamObserver<Embeddings.start_response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStartIndexingSessionMethod(), responseObserver);
    }

    /**
     */
    public void finishIndexingSession(Embeddings.finish_request request,
                                      io.grpc.stub.StreamObserver<Embeddings.finish_response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFinishIndexingSessionMethod(), responseObserver);
    }

    /**
     */
    public void getStorageStats(Embeddings.stats_request request,
                                io.grpc.stub.StreamObserver<Embeddings.stats_response> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetStorageStatsMethod(), responseObserver);
    }

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getEnsureVectorsPresentMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                Embeddings.present_request,
                Embeddings.present_response>(
                  this, METHODID_ENSURE_VECTORS_PRESENT)))
          .addMethod(
            getRemoveVectorsMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                Embeddings.remove_request,
                Embeddings.remove_response>(
                  this, METHODID_REMOVE_VECTORS)))
          .addMethod(
            getSearchMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                Embeddings.search_request,
                Embeddings.search_response>(
                  this, METHODID_SEARCH)))
          .addMethod(
            getClearStorageMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                Embeddings.clear_request,
                Embeddings.clear_response>(
                  this, METHODID_CLEAR_STORAGE)))
          .addMethod(
            getStartIndexingSessionMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                Embeddings.start_request,
                Embeddings.start_response>(
                  this, METHODID_START_INDEXING_SESSION)))
          .addMethod(
            getFinishIndexingSessionMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                Embeddings.finish_request,
                Embeddings.finish_response>(
                  this, METHODID_FINISH_INDEXING_SESSION)))
          .addMethod(
            getGetStorageStatsMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                Embeddings.stats_request,
                Embeddings.stats_response>(
                  this, METHODID_GET_STORAGE_STATS)))
          .build();
    }
  }

  /**
   */
  public static final class embedding_serviceStub extends io.grpc.stub.AbstractAsyncStub<embedding_serviceStub> {
    private embedding_serviceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected embedding_serviceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new embedding_serviceStub(channel, callOptions);
    }

    /**
     */
    public void ensureVectorsPresent(Embeddings.present_request request,
                                     io.grpc.stub.StreamObserver<Embeddings.present_response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getEnsureVectorsPresentMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void removeVectors(Embeddings.remove_request request,
                              io.grpc.stub.StreamObserver<Embeddings.remove_response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRemoveVectorsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void search(Embeddings.search_request request,
                       io.grpc.stub.StreamObserver<Embeddings.search_response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSearchMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void clearStorage(Embeddings.clear_request request,
                             io.grpc.stub.StreamObserver<Embeddings.clear_response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getClearStorageMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void startIndexingSession(Embeddings.start_request request,
                                     io.grpc.stub.StreamObserver<Embeddings.start_response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getStartIndexingSessionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void finishIndexingSession(Embeddings.finish_request request,
                                      io.grpc.stub.StreamObserver<Embeddings.finish_response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFinishIndexingSessionMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getStorageStats(Embeddings.stats_request request,
                                io.grpc.stub.StreamObserver<Embeddings.stats_response> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetStorageStatsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class embedding_serviceBlockingStub extends io.grpc.stub.AbstractBlockingStub<embedding_serviceBlockingStub> {
    private embedding_serviceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected embedding_serviceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new embedding_serviceBlockingStub(channel, callOptions);
    }

    /**
     */
    public Embeddings.present_response ensureVectorsPresent(Embeddings.present_request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getEnsureVectorsPresentMethod(), getCallOptions(), request);
    }

    /**
     */
    public Embeddings.remove_response removeVectors(Embeddings.remove_request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRemoveVectorsMethod(), getCallOptions(), request);
    }

    /**
     */
    public Embeddings.search_response search(Embeddings.search_request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSearchMethod(), getCallOptions(), request);
    }

    /**
     */
    public Embeddings.clear_response clearStorage(Embeddings.clear_request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getClearStorageMethod(), getCallOptions(), request);
    }

    /**
     */
    public Embeddings.start_response startIndexingSession(Embeddings.start_request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getStartIndexingSessionMethod(), getCallOptions(), request);
    }

    /**
     */
    public Embeddings.finish_response finishIndexingSession(Embeddings.finish_request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFinishIndexingSessionMethod(), getCallOptions(), request);
    }

    /**
     */
    public Embeddings.stats_response getStorageStats(Embeddings.stats_request request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetStorageStatsMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class embedding_serviceFutureStub extends io.grpc.stub.AbstractFutureStub<embedding_serviceFutureStub> {
    private embedding_serviceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @Override
    protected embedding_serviceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new embedding_serviceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<Embeddings.present_response> ensureVectorsPresent(
        Embeddings.present_request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getEnsureVectorsPresentMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<Embeddings.remove_response> removeVectors(
        Embeddings.remove_request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRemoveVectorsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<Embeddings.search_response> search(
        Embeddings.search_request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSearchMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<Embeddings.clear_response> clearStorage(
        Embeddings.clear_request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getClearStorageMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<Embeddings.start_response> startIndexingSession(
        Embeddings.start_request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getStartIndexingSessionMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<Embeddings.finish_response> finishIndexingSession(
        Embeddings.finish_request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFinishIndexingSessionMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<Embeddings.stats_response> getStorageStats(
        Embeddings.stats_request request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetStorageStatsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_ENSURE_VECTORS_PRESENT = 0;
  private static final int METHODID_REMOVE_VECTORS = 1;
  private static final int METHODID_SEARCH = 2;
  private static final int METHODID_CLEAR_STORAGE = 3;
  private static final int METHODID_START_INDEXING_SESSION = 4;
  private static final int METHODID_FINISH_INDEXING_SESSION = 5;
  private static final int METHODID_GET_STORAGE_STATS = 6;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final embedding_serviceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(embedding_serviceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_ENSURE_VECTORS_PRESENT:
          serviceImpl.ensureVectorsPresent((Embeddings.present_request) request,
              (io.grpc.stub.StreamObserver<Embeddings.present_response>) responseObserver);
          break;
        case METHODID_REMOVE_VECTORS:
          serviceImpl.removeVectors((Embeddings.remove_request) request,
              (io.grpc.stub.StreamObserver<Embeddings.remove_response>) responseObserver);
          break;
        case METHODID_SEARCH:
          serviceImpl.search((Embeddings.search_request) request,
              (io.grpc.stub.StreamObserver<Embeddings.search_response>) responseObserver);
          break;
        case METHODID_CLEAR_STORAGE:
          serviceImpl.clearStorage((Embeddings.clear_request) request,
              (io.grpc.stub.StreamObserver<Embeddings.clear_response>) responseObserver);
          break;
        case METHODID_START_INDEXING_SESSION:
          serviceImpl.startIndexingSession((Embeddings.start_request) request,
              (io.grpc.stub.StreamObserver<Embeddings.start_response>) responseObserver);
          break;
        case METHODID_FINISH_INDEXING_SESSION:
          serviceImpl.finishIndexingSession((Embeddings.finish_request) request,
              (io.grpc.stub.StreamObserver<Embeddings.finish_response>) responseObserver);
          break;
        case METHODID_GET_STORAGE_STATS:
          serviceImpl.getStorageStats((Embeddings.stats_request) request,
              (io.grpc.stub.StreamObserver<Embeddings.stats_response>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (embedding_serviceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .addMethod(getEnsureVectorsPresentMethod())
              .addMethod(getRemoveVectorsMethod())
              .addMethod(getSearchMethod())
              .addMethod(getClearStorageMethod())
              .addMethod(getStartIndexingSessionMethod())
              .addMethod(getFinishIndexingSessionMethod())
              .addMethod(getGetStorageStatsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
