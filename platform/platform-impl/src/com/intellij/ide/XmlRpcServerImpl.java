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
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import gnu.trove.THashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.xmlrpc.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.HttpRequestHandler;
import org.jetbrains.io.Responses;

import java.io.IOException;
import java.util.Map;

public class XmlRpcServerImpl implements XmlRpcServer {
  private static final Logger LOG = Logger.getInstance(XmlRpcServerImpl.class);

  private final Map<String, Object> handlerMapping;

  public XmlRpcServerImpl() {
    handlerMapping = new THashMap<String, Object>();
    for (XmlRpcHandlerBean handlerBean : Extensions.getExtensions(XmlRpcHandlerBean.EP_NAME)) {
      try {
        handlerMapping.put(handlerBean.name, handlerBean.instantiate());
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
      }
    }
    LOG.debug("XmlRpcServerImpl instantiated, handlers " + handlerMapping);
  }

  static final class XmlRpcRequestHandler extends HttpRequestHandler {
    @Override
    public boolean isSupported(FullHttpRequest request) {
      return request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.OPTIONS;
    }

    @Override
    public boolean process(QueryStringDecoder urlDecoder, FullHttpRequest request, ChannelHandlerContext context) throws IOException {
      return SERVICE.getInstance().process(urlDecoder.path(), request, context, null);
    }
  }

  @Override
  public boolean hasHandler(String name) {
    return handlerMapping.containsKey(name);
  }

  @Override
  public void addHandler(String name, Object handler) {
    handlerMapping.put(name, handler);
  }

  @Override
  public void removeHandler(String name) {
    handlerMapping.remove(name);
  }

  @Override
  public boolean process(@NotNull String path, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context, @Nullable Map<String, Object> handlers) throws IOException {
    if (!(path.isEmpty() || (path.length() == 1 && path.charAt(0) == '/') || path.equalsIgnoreCase("/RPC2"))) {
      return false;
    }

    if (request.getMethod() == HttpMethod.POST) {
      ByteBuf result;
      ByteBufInputStream in = new ByteBufInputStream(request.content());
      try {
        XmlRpcServerRequest xmlRpcServerRequest = new XmlRpcRequestProcessor().decodeRequest(in);

        if (StringUtil.isEmpty(xmlRpcServerRequest.getMethodName())) {
          LOG.warn("method name empty");
          return false;
        }

        Object response = invokeHandler(getHandler(xmlRpcServerRequest.getMethodName(), handlers == null ? handlerMapping : handlers), xmlRpcServerRequest);
        result = Unpooled.copiedBuffer(new XmlRpcResponseProcessor().encodeResponse(response, CharsetToolkit.UTF8));
      }
      catch (Throwable e) {
        context.channel().close();
        LOG.error(e);
        return true;
      }
      finally {
        in.close();
      }

      Responses.send(Responses.response("text/xml", result), context.channel(), request);
      return true;
    }
    else if (HttpMethod.POST.name().equals(request.headers().get("Access-Control-Request-Method"))) {
      LOG.assertTrue(request.getMethod() == HttpMethod.OPTIONS);
      Responses.sendOptionsResponse("POST, OPTIONS", request, context);
      return true;
    }
    return false;
  }

  private static Object getHandler(@NotNull String methodName, @NotNull Map<String, Object> handlers) {
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

  private static Object invokeHandler(@NotNull Object handler, XmlRpcServerRequest request) throws Exception {
    return (handler instanceof XmlRpcHandler ? (XmlRpcHandler)handler : new Invoker(handler)).execute(request.getMethodName(), request.getParameters());
  }
}