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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.net.NetUtils;
import io.netty.bootstrap.ServerBootstrap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.CustomPortServerManager;

import java.net.InetSocketAddress;

final class SubServer implements CustomPortServerManager.CustomPortService, Disposable {
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

    ServerBootstrap bootstrap = BuiltInServer.createServerBootstrap(server.eventLoopGroup, channelRegistrar, user.createXmlRpcHandlers());
    try {
      bootstrap.localAddress(user.isAvailableExternally() ? new InetSocketAddress(port) : new InetSocketAddress(NetUtils.getLoopbackAddress(), port));
      channelRegistrar.add(bootstrap.bind().syncUninterruptibly().channel());
      return true;
    }
    catch (Exception e) {
      try {
        NettyUtil.log(e, BuiltInServer.LOG);
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
}