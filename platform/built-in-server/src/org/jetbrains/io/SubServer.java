/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.net.NetKt;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.CustomPortServerManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

public final class SubServer implements CustomPortServerManager.CustomPortService, Disposable {
  private ChannelRegistrar channelRegistrar;

  private final CustomPortServerManager user;
  private final BuiltInServer server;

  public SubServer(@NotNull CustomPortServerManager user, @NotNull BuiltInServer server) {
    this.user = user;
    this.server = server;

    user.setManager(this);
  }

  public boolean bind(int port) {
    if (port == server.getPort() || port == -1) {
      return true;
    }

    if (channelRegistrar == null) {
      Disposer.register(server, this);
      channelRegistrar = new ChannelRegistrar();
    }

    ServerBootstrap bootstrap = NettyKt.serverBootstrap(server.getEventLoopGroup());
    Map<String, Object> xmlRpcHandlers = user.createXmlRpcHandlers();
    if (xmlRpcHandlers == null) {
      BuiltInServer.configureChildHandler(bootstrap, channelRegistrar, null);
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

    try {
      bootstrap.localAddress(user.isAvailableExternally() ? new InetSocketAddress(port) : NetKt.loopbackSocketAddress(port));
      channelRegistrar.add(bootstrap.bind().syncUninterruptibly().channel());
      return true;
    }
    catch (Exception e) {
      try {
        NettyUtil.log(e, Logger.getInstance(BuiltInServer.class));
      }
      finally {
        user.cannotBind(e, port);
      }
      return false;
    }
  }

  @Override
  public boolean isBound() {
    return channelRegistrar != null && !channelRegistrar.isEmpty();
  }

  private void stop() {
    if (channelRegistrar != null) {
      channelRegistrar.close(false);
    }
  }

  @Override
  public boolean rebind() {
    stop();
    return bind(user.getPort());
  }

  @Override
  public void dispose() {
    stop();
    user.setManager(null);
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