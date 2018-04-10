// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import java.net.InetSocketAddress;
import java.util.Map;

import static com.intellij.util.io.NettyKt.serverBootstrap;

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

    ServerBootstrap bootstrap = serverBootstrap(server.getEventLoopGroup());
    Map<String, Object> xmlRpcHandlers = user.createXmlRpcHandlers();
    if (xmlRpcHandlers == null) {
      BuiltInServer.configureChildHandler(bootstrap, channelRegistrar, null);
    }
    else {
      final XmlRpcDelegatingHttpRequestHandler handler = new XmlRpcDelegatingHttpRequestHandler(xmlRpcHandlers);
      bootstrap.childHandler(new ChannelInitializer() {
        @Override
        protected void initChannel(Channel channel) {
          channel.pipeline().addLast(channelRegistrar);
          NettyUtil.addHttpServerCodec(channel.pipeline());
          channel.pipeline().addLast(handler);
        }
      });
    }

    try {
      bootstrap.localAddress(user.isAvailableExternally() ? new InetSocketAddress(port) : NetKt.loopbackSocketAddress(port));
      channelRegistrar.setServerChannel(bootstrap.bind().syncUninterruptibly().channel(), false);
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
      channelRegistrar.close();
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
    protected boolean process(@NotNull ChannelHandlerContext context, @NotNull FullHttpRequest request, @NotNull QueryStringDecoder urlDecoder) {
      if (handlers.isEmpty()) {
        // not yet initialized, for example, P2PTransport could add handlers after we bound.
        return false;
      }

      return request.method() == HttpMethod.POST && XmlRpcServer.SERVICE.getInstance().process(urlDecoder.path(), request, context, handlers);
    }
  }
}