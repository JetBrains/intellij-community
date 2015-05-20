/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.io;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.net.NetUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.CustomPortServerManager;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Map;

public class BuiltInServer implements Disposable {
  static final Logger LOG = Logger.getInstance(BuiltInServer.class);

  private final ChannelRegistrar channelRegistrar = new ChannelRegistrar();

  public boolean isRunning() {
    return !channelRegistrar.isEmpty();
  }

  public void start(int port) {
    start(1, port, 1, false);
  }

  public int start(int workerCount, int firstPort, int portsCount, boolean tryAnyPort) {
    if (isRunning()) {
      throw new IllegalStateException("server already started");
    }

    NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(workerCount, PooledThreadExecutor.INSTANCE);
    ServerBootstrap bootstrap = createServerBootstrap(eventLoopGroup, channelRegistrar, null);
    int port = bind(firstPort, portsCount, tryAnyPort, bootstrap);
    bindCustomPorts(firstPort, port, eventLoopGroup);
    return port;
  }

  @NotNull
  static ServerBootstrap createServerBootstrap(@NotNull EventLoopGroup eventLoopGroup,
                                               @NotNull final ChannelRegistrar channelRegistrar,
                                               @Nullable Map<String, Object> xmlRpcHandlers) {
    ServerBootstrap bootstrap = NettyUtil.nioServerBootstrap(eventLoopGroup);
    if (xmlRpcHandlers == null) {
      final PortUnificationServerHandler portUnificationServerHandler = new PortUnificationServerHandler();
      bootstrap.childHandler(new ChannelInitializer() {
        @Override
        protected void initChannel(Channel channel) throws Exception {
          channel.pipeline().addLast(channelRegistrar, portUnificationServerHandler);
        }
      });
    }
    else {
      final XmlRpcDelegatingHttpRequestHandler handler = new XmlRpcDelegatingHttpRequestHandler(xmlRpcHandlers);
      bootstrap.childHandler(new ChannelInitializer() {
        @Override
        protected void initChannel(Channel channel) throws Exception {
          channel.pipeline().addLast(channelRegistrar);
          NettyUtil.addHttpServerCodec(channel.pipeline());
          channel.pipeline().addLast(handler);
        }
      });
    }
    return bootstrap;
  }

  private void bindCustomPorts(int firstPort, int port, NioEventLoopGroup eventLoopGroup) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    for (CustomPortServerManager customPortServerManager : CustomPortServerManager.EP_NAME.getExtensions()) {
      try {
        int customPortServerManagerPort = customPortServerManager.getPort();
        SubServer subServer = new SubServer(customPortServerManager, eventLoopGroup);
        Disposer.register(this, subServer);
        if (customPortServerManager.isAvailableExternally() || (customPortServerManagerPort != firstPort && customPortServerManagerPort != port)) {
          subServer.bind(customPortServerManagerPort);
        }
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  private int bind(int firstPort, int portsCount, boolean tryAnyPort, ServerBootstrap bootstrap) {
    InetAddress loopbackAddress = NetUtils.getLoopbackAddress();
    for (int i = 0; i < portsCount; i++) {
      int port = firstPort + i;

      // we check if any port free too
      if (!SystemInfo.isLinux && (!SystemInfo.isWindows || SystemInfo.isWinVistaOrNewer)) {
        try {
          ServerSocket serverSocket = new ServerSocket();
          try {
            serverSocket.bind(new InetSocketAddress(port), 1);
          }
          finally {
            serverSocket.close();
          }
        }
        catch (IOException ignored) {
          continue;
        }
      }

      ChannelFuture future = bootstrap.bind(loopbackAddress, port).awaitUninterruptibly();
      if (future.isSuccess()) {
        channelRegistrar.add(future.channel());
        return port;
      }
      else if (!tryAnyPort && i == (portsCount - 1)) {
        LOG.error(future.cause());
        return -1;
      }
    }

    LOG.info("We cannot bind to our default range, so, try to bind to any free port");
    ChannelFuture future = bootstrap.bind(loopbackAddress, 0).awaitUninterruptibly();
    if (future.isSuccess()) {
      channelRegistrar.add(future.channel());
      return ((InetSocketAddress)future.channel().localAddress()).getPort();
    }
    else {
      LOG.error(future.cause());
      return -1;
    }
  }

  @Override
  public void dispose() {
    channelRegistrar.close();
    LOG.info("web server stopped");
  }

  public static void replaceDefaultHandler(@NotNull ChannelHandlerContext context, @NotNull ChannelHandler channelHandler) {
    context.pipeline().replace(DelegatingHttpRequestHandler.class, "replacedDefaultHandler", channelHandler);
  }

  @ChannelHandler.Sharable
  private static final class XmlRpcDelegatingHttpRequestHandler extends DelegatingHttpRequestHandlerBase {
    private final Map<String, Object> handlers;

    public XmlRpcDelegatingHttpRequestHandler(Map<String, Object> handlers) {
      this.handlers = handlers;
    }

    @Override
    protected boolean process(@NotNull ChannelHandlerContext context, @NotNull FullHttpRequest request, @NotNull QueryStringDecoder urlDecoder) throws IOException {
      if (handlers.isEmpty()) {
        // not yet initialized, for example, P2PTransport could add handlers after we bound.
        return false;
      }

      return request.method() == HttpMethod.POST && XmlRpcServer.SERVICE.getInstance().process(urlDecoder.path(), request, context, handlers);
    }
  }
}