/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.util.Consumer;
import gnu.trove.THashMap;
import org.apache.xmlrpc.*;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.Responses;

import java.util.Arrays;

@ChannelHandler.Sharable
public class XmlRpcServerImpl extends SimpleChannelUpstreamHandler implements XmlRpcServer, Consumer<ChannelPipeline> {
  private static final Logger LOG = Logger.getInstance(XmlRpcServerImpl.class);

  private final XmlRpcHandlerMappingImpl handlerMapping = new LoggingDefaultHandlerMapping();
  // idea doesn't use authentication
  private final XmlRpcContext xmlRpcContext = new XmlRpcContext() {
    @Nullable
    @Override
    public String getUserName() {
      return null;
    }

    @Nullable
    @Override
    public String getPassword() {
      return null;
    }

    @Override
    public XmlRpcHandlerMapping getHandlerMapping() {
      return handlerMapping;
    }
  };

  public XmlRpcServerImpl() {
    for (XmlRpcHandlerBean handlerBean : Extensions.getExtensions(XmlRpcHandlerBean.EP_NAME)) {
      final Object handler;
      try {
        handler = handlerBean.instantiate();
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
        continue;
      }
      handlerMapping.addHandler(handlerBean.name, handler);
    }

    LOG.info("XmlRpcServerImpl instantiated, handlers " + handlerMapping);
  }

  @Override
  public void consume(ChannelPipeline pipeline) {
    pipeline.addLast("pluggable_xmlRpc", this);
  }

  @Override
  public boolean hasHandler(String name) {
    return handlerMapping.handlers.containsKey(name);
  }

  public void addHandler(String name, Object handler) {
    handlerMapping.addHandler(name, handler);
  }

  public void removeHandler(String name) {
    handlerMapping.removeHandler(name);
  }

  public void messageReceived(ChannelHandlerContext context, MessageEvent e) throws Exception {
    if (e.getMessage() instanceof HttpRequest) {
      HttpRequest request = (HttpRequest)e.getMessage();

      if (request.getMethod() == HttpMethod.POST) {
        ChannelBuffer result;
        ChannelBufferInputStream in = new ChannelBufferInputStream(request.getContent());
        try {
          result = ChannelBuffers.copiedBuffer(new XmlRpcWorker(handlerMapping).execute(in, xmlRpcContext));
        }
        catch (Throwable ex) {
          context.getChannel().close();
          LOG.error(ex);
          return;
        }
        finally {
          in.close();
        }

        HttpResponse response = Responses.create("text/xml");
        response.setContent(result);
        Responses.send(response, request, context);
        return;
      }

      if (request.getMethod() == HttpMethod.OPTIONS &&
          HttpMethod.POST.getName().equals(request.getHeader("Access-Control-Request-Method"))) {
        HttpResponse response = Responses.create("text/plain");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        Responses.send(response, request, context);
        return;
      }
    }

    context.sendUpstream(e);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent e) throws Exception {
    try {
      LOG.error(e.getCause());
    }
    finally {
      e.getChannel().close();
    }
  }

  private static class XmlRpcHandlerMappingImpl implements XmlRpcHandlerMapping {
    protected final THashMap<String, Object> handlers = new THashMap<String, Object>();

    public void addHandler(@NotNull String handlerName, @NotNull Object handler) {
      if (handler instanceof XmlRpcHandler) {
        handlers.put(handlerName, handler);
      }
      else {
        handlers.put(handlerName, new Invoker(handler));
      }
    }

    public void removeHandler(String handlerName) {
      handlers.remove(handlerName);
    }

    public Object getHandler(String methodName) {
      Object handler = null;
      String handlerName = null;
      int dot = methodName.lastIndexOf('.');
      if (dot > -1) {
        handlerName = methodName.substring(0, dot);
        handler = handlers.get(handlerName);
      }

      if (handler != null) {
        return handler;
      }

      IllegalStateException exception;
      if (dot > -1) {
        exception = new IllegalStateException("RPC handler object \"" + handlerName + "\" not found");
      }
      else {
        exception = new IllegalStateException("RPC handler object not found for \"" + methodName);
      }

      LOG.error(exception);
      throw exception;
    }
  }

  private static class LoggingDefaultHandlerMapping extends XmlRpcHandlerMappingImpl {
    @Override
    public void addHandler(@NotNull String handlerName, @NotNull Object handler) {
      LOG.info(String.format("addHandler: handlerName: %s, handler: %s%s", handlerName, handler, getHandlers()));
      super.addHandler(handlerName, handler);
    }

    @Override
    public void removeHandler(String handlerName) {
      LOG.debug(String.format("removeHandler: handlerName: %s%s", handlerName, getHandlers()));
      super.removeHandler(handlerName);
    }

    @Override
    public Object getHandler(String methodName) {
      LOG.info(String.format("getHandler: methodName: %s%s", methodName, getHandlers()));
      return super.getHandler(methodName);
    }

    private String getHandlers() {
      return String.format("%nhandlers: %s %s", Arrays.toString(handlers.keySet().toArray()), Arrays.toString(handlers.values().toArray()));
    }

    @Override
    public String toString() {
      return getHandlers();
    }
  }
}