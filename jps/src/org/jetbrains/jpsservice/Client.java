package org.jetbrains.jpsservice;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.protobuf.ProtobufDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufEncoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import org.jboss.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jpsservice.impl.JpsClientMessageHandler;
import org.jetbrains.jpsservice.impl.JpsProtoUtil;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 *         Date: 8/11/11
 */
public class Client {

  private final ChannelPipelineFactory myPipelineFactory;
  private final ChannelFactory myChannelFactory;
  private ChannelFuture myConnectFuture;
  private Map<UUID, JpsServerResponseHandler> myResponseHandlers = new ConcurrentHashMap<UUID, JpsServerResponseHandler>();
  private Queue<RequestHandlerPair> myRequestQueue = new LinkedBlockingQueue<RequestHandlerPair>();
  private AtomicBoolean myRequestInProgress = new AtomicBoolean(false);

  public Client() {
    myChannelFactory = new NioClientSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool()
    );

    myPipelineFactory = new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new ProtobufVarint32FrameDecoder(),
          new ProtobufDecoder(JpsRemoteProto.Message.getDefaultInstance()),
          new ProtobufVarint32LengthFieldPrepender(),
          new ProtobufEncoder(),
          new JpsClientMessageHandler() {
            protected JpsServerResponseHandler getHandler(JpsRemoteProto.Message message) {
              // todo: do we need a default handler?
              final JpsRemoteProto.Message.UUID messageUuid = message.getMessageUuid();
              return myResponseHandlers.remove(new UUID(messageUuid.getMostSigBits(), messageUuid.getLeastSigBits()));
            }
          }
        );
      }
    };
  }

  public void ensureRequestQueueEmpty() {
    while (myRequestQueue.peek() != null && !myRequestInProgress.get()) {
      try {
        Thread.sleep(5);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public boolean connect(final String host, final int port) {
    final ClientBootstrap bootstrap = new ClientBootstrap(myChannelFactory);
    bootstrap.setPipelineFactory(myPipelineFactory);

    bootstrap.setOption("tcpNoDelay", true);
    bootstrap.setOption("keepAlive", true);
    final ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port));

    myConnectFuture = future;
    future.awaitUninterruptibly();
    return future.isSuccess();
  }

  @Nullable
  public Throwable getFailureCause() {
    final ChannelFuture future = myConnectFuture;
    return future != null? future.getCause() : null;
  }

  public void disconnect() {
    final ChannelFuture future = myConnectFuture;
    if (future != null) {
      try {
        final ChannelFuture closeFuture = future.getChannel().close();
        closeFuture.awaitUninterruptibly();
      }
      finally {
        myChannelFactory.releaseExternalResources();
      }
    }
  }

  public void sendCompileRequest(JpsRemoteProto.Message.Request.CompileRequest.Command command, String projectId, List<String> modules, JpsServerResponseHandler handler) {
    final JpsRemoteProto.Message.Request.CompileRequest.Builder compileRequest = JpsRemoteProto.Message.Request.CompileRequest.newBuilder();
    compileRequest.setCommand(command);
    compileRequest.setProjectId(projectId);
    if (modules != null && modules.size() > 0) {
      compileRequest.addAllModuleName(modules);
    }
    queueRequest(JpsRemoteProto.Message.Request.newBuilder().setCompileRequest(compileRequest).build(), handler);
  }

  public void sendStatusRequest(UUID sessionId, JpsServerResponseHandler handler) {
    final JpsRemoteProto.Message.Request.StatusRequest.Builder statusRequest = JpsRemoteProto.Message.Request.StatusRequest.newBuilder();
    statusRequest.setSessionId(JpsProtoUtil.createProtoUUID(sessionId));
    queueRequest(JpsRemoteProto.Message.Request.newBuilder().setStatusRequest(statusRequest).build(), handler);
  }

  private void queueRequest(JpsRemoteProto.Message.Request request, @Nullable final JpsServerResponseHandler handler) {
    myRequestQueue.add(new RequestHandlerPair(request, handler));
    processQueue();
  }

  private void processQueue() {
    if (!myRequestInProgress.getAndSet(true)) {
      final RequestHandlerPair next = myRequestQueue.poll();
      if (next != null) {
        sendRequest(next.request, new JpsServerResponseHandler() {
          public void handleCompileResponse(JpsRemoteProto.Message.Response.CompileResponse compileResponse) {
            if (next.handler != null) {
              next.handler.handleCompileResponse(compileResponse);
            }
            myRequestInProgress.set(false);
            processQueue();
          }

          public void handleStatusResponse(JpsRemoteProto.Message.Response.StatusResponse response) {
            if (next.handler != null) {
              next.handler.handleStatusResponse(response);
            }
            myRequestInProgress.set(false);
            processQueue();
          }

          public void handleFailure(JpsRemoteProto.Message.Failure failure) {
            if (next.handler != null) {
              next.handler.handleFailure(failure);
            }
            myRequestInProgress.set(false);
            processQueue();
          }
        });
      }
      else {
        myRequestInProgress.set(false);
      }
    }
  }

  private ChannelFuture sendRequest(JpsRemoteProto.Message.Request request, JpsServerResponseHandler handler) {
    final UUID requestUUID = UUID.randomUUID();
    final JpsRemoteProto.Message msg = JpsRemoteProto.Message.newBuilder().setKind(JpsRemoteProto.Message.Kind.REQUEST).setMessageUuid(JpsProtoUtil.createProtoUUID(requestUUID)).setRequest(request).build();
    myResponseHandlers.put(requestUUID, handler);
    return Channels.write(myConnectFuture.getChannel(), msg);
  }

  private static final class RequestHandlerPair {
    final JpsRemoteProto.Message.Request request;
    @Nullable final JpsServerResponseHandler handler;

    RequestHandlerPair(JpsRemoteProto.Message.Request request, JpsServerResponseHandler handler) {
      this.request = request;
      this.handler = handler;
    }
  }
}
