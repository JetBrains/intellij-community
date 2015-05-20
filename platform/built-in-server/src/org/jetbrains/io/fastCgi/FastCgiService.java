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

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.SingleConnectionNetService;
import org.jetbrains.io.ChannelExceptionHandler;
import org.jetbrains.io.MessageDecoder;
import org.jetbrains.io.NettyUtil;
import org.jetbrains.io.Responses;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// todo send FCGI_ABORT_REQUEST if client channel disconnected
public abstract class FastCgiService extends SingleConnectionNetService {
  protected static final Logger LOG = Logger.getInstance(FastCgiService.class);

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
          sendBadGateway(channel);
        }
      }
    }
  }

  private static void sendBadGateway(@NotNull Channel channel) {
    try {
      if (channel.isActive()) {
        Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel);
      }
    }
    catch (Throwable e) {
      NettyUtil.log(e, LOG);
    }
  }

  @Override
  protected void configureBootstrap(@NotNull Bootstrap bootstrap, @NotNull final Consumer<String> errorOutputConsumer) {
    bootstrap.handler(new ChannelInitializer() {
      @Override
      protected void initChannel(Channel channel) throws Exception {
        channel.pipeline().addLast("fastCgiDecoder", new FastCgiDecoder(errorOutputConsumer, FastCgiService.this));
        channel.pipeline().addLast("exceptionHandler", ChannelExceptionHandler.getInstance());
      }
    });
  }

  public void send(@NotNull final FastCgiRequest fastCgiRequest, @NotNull ByteBuf content) {
    final ByteBuf notEmptyContent;
    if (content.isReadable()) {
      content.retain();
      notEmptyContent = content;
      notEmptyContent.touch();
    }
    else {
      notEmptyContent = null;
    }

    try {
      if (processHandler.has()) {
        fastCgiRequest.writeToServerChannel(notEmptyContent, processChannel);
      }
      else {
        processHandler.get()
          .done(new Consumer<OSProcessHandler>() {
            @Override
            public void consume(OSProcessHandler osProcessHandler) {
              fastCgiRequest.writeToServerChannel(notEmptyContent, processChannel);
            }
          })
          .rejected(new Consumer<Throwable>() {
            @Override
            public void consume(Throwable error) {
              LOG.error(error);
              handleError(fastCgiRequest, notEmptyContent);
            }
          });
      }
    }
    catch (Throwable e) {
      LOG.error(e);
      handleError(fastCgiRequest, notEmptyContent);
    }
  }

  private void handleError(@NotNull FastCgiRequest fastCgiRequest, @Nullable ByteBuf content) {
    try {
      if (content != null && content.refCnt() != 0) {
        content.release();
      }
    }
    finally {
      Channel channel = requests.remove(fastCgiRequest.requestId);
      if (channel != null) {
        sendBadGateway(channel);
      }
    }
  }

  public int allocateRequestId(@NotNull Channel channel) {
    int requestId = requestIdCounter.getAndIncrement();
    if (requestId >= Short.MAX_VALUE) {
      requestIdCounter.set(0);
      requestId = requestIdCounter.getAndDecrement();
    }
    requests.put(requestId, channel);
    return requestId;
  }

  void responseReceived(int id, @Nullable ByteBuf buffer) {
    Channel channel = requests.remove(id);
    if (channel == null || !channel.isActive()) {
      if (buffer != null) {
        buffer.release();
      }
      return;
    }

    if (buffer == null) {
      Responses.sendStatus(HttpResponseStatus.BAD_GATEWAY, channel);
      return;
    }

    HttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer);
    try {
      parseHeaders(httpResponse, buffer);
      Responses.addServer(httpResponse);
      if (!HttpHeaderUtil.isContentLengthSet(httpResponse)) {
        HttpHeaderUtil.setContentLength(httpResponse, buffer.readableBytes());
      }
    }
    catch (Throwable e) {
      buffer.release();
      try {
        LOG.error(e);
      }
      finally {
        Responses.sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, channel);
      }
      return;
    }
    channel.writeAndFlush(httpResponse);
  }

  private static void parseHeaders(@NotNull HttpResponse response, @NotNull ByteBuf buffer) {
    StringBuilder builder = new StringBuilder();
    while (buffer.isReadable()) {
      builder.setLength(0);

      String key = null;
      boolean valueExpected = true;
      while (true) {
        int b = buffer.readByte();
        if (b < 0 || b == '\n') {
          break;
        }

        if (b != '\r') {
          if (valueExpected && b == ':') {
            valueExpected = false;

            key = builder.toString();
            builder.setLength(0);
            MessageDecoder.skipWhitespace(buffer);
          }
          else {
            builder.append((char)b);
          }
        }
      }

      if (builder.length() == 0) {
        // end of headers
        return;
      }

      // skip standard headers
      if (StringUtil.isEmpty(key) || StringUtilRt.startsWithIgnoreCase(key, "http") || StringUtilRt.startsWithIgnoreCase(key, "X-Accel-")) {
        continue;
      }

      String value = builder.toString();
      if (key.equalsIgnoreCase("status")) {
        int index = value.indexOf(' ');
        if (index == -1) {
          LOG.warn("Cannot parse status: " + value);
          response.setStatus(HttpResponseStatus.OK);
        }
        else {
          response.setStatus(HttpResponseStatus.valueOf(Integer.parseInt(value.substring(0, index))));
        }
      }
      else if (!(key.startsWith("http") || key.startsWith("HTTP"))) {
        response.headers().add(key, value);
      }
    }
  }
}