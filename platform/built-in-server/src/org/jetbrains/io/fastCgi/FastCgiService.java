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
package org.jetbrains.io.fastCgi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.builtInWebServer.SingleConnectionNetService;
import org.jetbrains.io.ChannelExceptionHandler;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.io.Responses;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// todo send FCGI_ABORT_REQUEST if client channel disconnected
public abstract class FastCgiService extends SingleConnectionNetService {
  static final Logger LOG = Logger.getInstance(FastCgiService.class);

  private final AtomicInteger requestIdCounter = new AtomicInteger();
  protected final ConcurrentIntObjectMap<Channel> requests = ContainerUtil.createConcurrentIntObjectMap();

  public FastCgiService(@NotNull Project project) {
    super(project);
  }

  @Override
  protected void closeProcessConnections() {
    try {
      super.closeProcessConnections();
    }
    finally {
      requestIdCounter.set(0);
      if (!requests.isEmpty()) {
        List<Channel> waitingClients = ContainerUtil.toList(requests.elements());
        requests.clear();
        for (Channel channel : waitingClients) {
          try {
            if (channel.isActive()) {
              Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel);
            }
          }
          catch (Throwable e) {
            NettyUtil.log(e, LOG);
          }
        }
      }
    }
  }

  @Override
  protected void configureBootstrap(@NotNull Bootstrap bootstrap, @NotNull final Consumer<String> errorOutputConsumer) {
    final FastCgiChannelHandler fastCgiChannelHandler = new FastCgiChannelHandler(requests);
    bootstrap.handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast(new FastCgiDecoder(errorOutputConsumer), fastCgiChannelHandler, ChannelExceptionHandler.getInstance());
      }
    });
  }

  public void send(final FastCgiRequest fastCgiRequest, final ByteBuf content) {
    content.retain();

    if (processHandler.has()) {
      fastCgiRequest.writeToServerChannel(content, processChannel);
    }
    else {
      processHandler.get().doWhenDone(new Runnable() {
        @Override
        public void run() {
          fastCgiRequest.writeToServerChannel(content, processChannel);
        }
      }).doWhenRejected(new Runnable() {
        @Override
        public void run() {
          content.release();
          Channel channel = requests.get(fastCgiRequest.requestId);
          if (channel != null && channel.isActive()) {
            Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel);
          }
        }
      });
    }
  }

  public int allocateRequestId(Channel channel) {
    int requestId = requestIdCounter.getAndIncrement();
    if (requestId >= Short.MAX_VALUE) {
      requestIdCounter.set(0);
      requestId = requestIdCounter.getAndDecrement();
    }
    requests.put(requestId, channel);
    return requestId;
  }
}