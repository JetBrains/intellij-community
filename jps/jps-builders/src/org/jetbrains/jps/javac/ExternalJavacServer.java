/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.javac;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.Semaphore;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.java.JavaCompilingTool;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.GlobalContextKey;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;
import org.jetbrains.jps.service.SharedThreadPool;

import javax.tools.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/22/12                       
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ExternalJavacServer {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.javac.ExternalJavacServer");
  public static final GlobalContextKey<ExternalJavacServer> KEY = GlobalContextKey.create("_external_javac_server_");
  
  public static final int DEFAULT_SERVER_PORT = 7878;
  private static final AttributeKey<JavacProcessDescriptor> SESSION_DESCRIPTOR = AttributeKey.valueOf("ExternalJavacServer.JavacProcessDescriptor");

  private ChannelRegistrar myChannelRegistrar;
  private final Map<UUID, JavacProcessDescriptor> myMessageHandlers = new HashMap<UUID, JavacProcessDescriptor>();
  private int myListenPort = DEFAULT_SERVER_PORT;

  public void start(int listenPort) {
    final ServerBootstrap bootstrap = new ServerBootstrap().group(new NioEventLoopGroup(1, SharedThreadPool.getInstance())).channel(NioServerSocketChannel.class);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
    myChannelRegistrar = new ChannelRegistrar();
    final ChannelHandler compilationRequestsHandler = new CompilationRequestsHandler();
    bootstrap.childHandler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(myChannelRegistrar,
                                   new ProtobufVarint32FrameDecoder(),
                                   new ProtobufDecoder(JavacRemoteProto.Message.getDefaultInstance()),
                                   new ProtobufVarint32LengthFieldPrepender(),
                                   new ProtobufEncoder(),
                                   compilationRequestsHandler);
      }
    });
    myChannelRegistrar.add(bootstrap.bind(listenPort).syncUninterruptibly().channel());
    myListenPort = listenPort;
  }
  
  private static int getExternalJavacHeapSize(CompileContext context) {
    final JpsProject project = context.getProjectDescriptor().getProject();
    final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getOrCreateCompilerConfiguration(project);
    final JpsJavaCompilerOptions options = config.getCurrentCompilerOptions();
    return options.MAXIMUM_HEAP_SIZE;
  }

  
  public boolean forkJavac(CompileContext context, List<String> options,
                           List<String> vmOptions, Collection<File> files,
                           Collection<File> classpath,
                           Collection<File> platformCp,
                           Collection<File> sourcePath,
                           Map<File, Set<File>> outs,
                           DiagnosticOutputConsumer diagnosticSink,
                           OutputFileConsumer outputSink,
                           final String javaHome, final JavaCompilingTool compilingTool) {
    final ExternalJavacMessageHandler rh = new ExternalJavacMessageHandler(diagnosticSink, outputSink, getEncodingName(options));
    final JavacRemoteProto.Message.Request request = JavacProtoUtil.createCompilationRequest(options, files, classpath, platformCp, sourcePath, outs);
    final UUID uuid = UUID.randomUUID();
    final JavacProcessDescriptor processDescriptor = new JavacProcessDescriptor(uuid, rh, request);
    synchronized (myMessageHandlers) {
      myMessageHandlers.put(uuid, processDescriptor);
    }
    try {
      final JavacServerBootstrap.ExternalJavacProcessHandler processHandler = JavacServerBootstrap.launchExternalJavacProcess(
        uuid, javaHome, getExternalJavacHeapSize(context), myListenPort, Utils.getSystemRoot(), vmOptions, compilingTool
      );

      while (!processDescriptor.waitFor(300L)) {
        if (processHandler.isProcessTerminated() && processDescriptor.channel == null && processHandler.getExitCode() != 0) {
          // process terminated abnormally and no communication took place
          processDescriptor.setDone();
          break;
        }
        if (context.getCancelStatus().isCanceled()) {
          processDescriptor.cancelBuild();
        }
      }

      return rh.isTerminatedSuccessfully();
    }
    catch (Throwable e) {
      LOG.info(e);
      diagnosticSink.report(new PlainMessageDiagnostic(Diagnostic.Kind.ERROR, e.getMessage()));
    }
    finally {
      unregisterMessageHandler(uuid);
    }
    return false;
  }

  private void unregisterMessageHandler(UUID uuid) {
    final JavacProcessDescriptor descriptor;
    synchronized (myMessageHandlers) {
      descriptor = myMessageHandlers.remove(uuid);
    }
    if (descriptor != null) {
      descriptor.setDone();
    }
  }

  @Nullable
  private static String getEncodingName(List<String> options) {
    boolean found = false;
    for (String option : options) {
      if (found) {
        return option;
      }
      if ("-encoding".equalsIgnoreCase(option)) {
        found = true;
      }
    }
    return null;
  }
  
  public void stop() {
    myChannelRegistrar.close().awaitUninterruptibly();
  }

  @ChannelHandler.Sharable
  private class CompilationRequestsHandler extends SimpleChannelInboundHandler<JavacRemoteProto.Message> {
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
      JavacProcessDescriptor descriptor = ctx.attr(SESSION_DESCRIPTOR).get();
      if (descriptor != null) {
        descriptor.setDone();
      }
      super.channelUnregistered(ctx);
    }

    @Override
    public void channelRead0(final ChannelHandlerContext context, JavacRemoteProto.Message message) throws Exception {
      JavacProcessDescriptor descriptor = context.attr(SESSION_DESCRIPTOR).get();
  
      UUID sessionId;
      if (descriptor == null) {
        // this is the first message for this session, so fill session data with missing info
        sessionId = JavacProtoUtil.fromProtoUUID(message.getSessionId());
  
        descriptor = myMessageHandlers.get(sessionId);
        if (descriptor != null) {
          descriptor.channel = context.channel();
          context.attr(SESSION_DESCRIPTOR).set(descriptor);
        }
      }
      else {
        sessionId = descriptor.sessionId;
      }
  
      final ExternalJavacMessageHandler handler = descriptor != null? descriptor.handler : null;

      final JavacRemoteProto.Message.Type messageType = message.getMessageType();

      JavacRemoteProto.Message reply = null;
      try {
        if (messageType == JavacRemoteProto.Message.Type.RESPONSE) {
          final JavacRemoteProto.Message.Response response = message.getResponse();
          final JavacRemoteProto.Message.Response.Type responseType = response.getResponseType();
          if (handler != null) {
            if (responseType == JavacRemoteProto.Message.Response.Type.REQUEST_ACK) {
              final JavacRemoteProto.Message.Request request = descriptor.request;
              if (request != null) {
                reply = JavacProtoUtil.toMessage(sessionId, request);
                descriptor.request = null;
              }
            }
            else {
              final boolean terminateOk = handler.handleMessage(message);
              if (terminateOk) {
                descriptor.setDone();
              }
            }
          }
          else {
            reply = JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createCancelRequest());
          }
        }
        else {
          reply = JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createFailure("Unsupported message: " + messageType.name(), null));
        }
      }
      finally {
        if (reply != null) {
          context.channel().writeAndFlush(reply);  
        }
      }
    }
  }

  @ChannelHandler.Sharable
  private static final class ChannelRegistrar extends ChannelInboundHandlerAdapter {
    private final ChannelGroup openChannels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

    public boolean isEmpty() {
      return openChannels.isEmpty();
    }

    public void add(@NotNull Channel serverChannel) {
      assert serverChannel instanceof ServerChannel;
      openChannels.add(serverChannel);
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
      // we don't need to remove channel on close - ChannelGroup do it
      openChannels.add(context.channel());
      super.channelActive(context);
    }

    public ChannelGroupFuture close() {
      EventLoopGroup eventLoopGroup = null;
      for (Channel channel : openChannels) {
        if (channel instanceof ServerChannel) {
          eventLoopGroup = channel.eventLoop().parent();
          break;
        }
      }

      ChannelGroupFuture future;
      try {
        future = openChannels.close();
      }
      finally {
        assert eventLoopGroup != null;
        eventLoopGroup.shutdownGracefully(0, 15, TimeUnit.SECONDS);
      }
      return future;
    }
  }

  private static class JavacProcessDescriptor {
    @NotNull
    final UUID sessionId;
    @NotNull
    final ExternalJavacMessageHandler handler;
    volatile JavacRemoteProto.Message.Request request;
    volatile Channel channel;
    private final Semaphore myDone = new Semaphore();

    public JavacProcessDescriptor(@NotNull UUID sessionId, @NotNull ExternalJavacMessageHandler handler, @NotNull JavacRemoteProto.Message.Request request) {
      this.sessionId = sessionId;
      this.handler = handler;
      this.request = request;
      myDone.down();
    }

    public void cancelBuild() {
      if (channel != null) {
        channel.writeAndFlush(JavacProtoUtil.toMessage(sessionId, JavacProtoUtil.createCancelRequest()));
      }
    }
    
    public void setDone() {
      myDone.up();
    }
    
    
    public boolean waitFor(long timeout) {
      return myDone.waitFor(timeout);
    }
    
  }
  
}