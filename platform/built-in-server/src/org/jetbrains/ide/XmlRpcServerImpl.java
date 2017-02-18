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
package org.jetbrains.ide;

import com.intellij.ide.XmlRpcServer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import gnu.trove.THashMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.xmlrpc.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.Responses;
import org.xml.sax.SAXParseException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Vector;

public class XmlRpcServerImpl implements XmlRpcServer {
  private static final Logger LOG = Logger.getInstance(XmlRpcServerImpl.class);

  private final Map<String, Object> handlerMapping = new THashMap<>();

  public XmlRpcServerImpl() {
  }

  static final class XmlRpcRequestHandler extends HttpRequestHandler {
    @Override
    public boolean isSupported(@NotNull FullHttpRequest request) {
      return request.method() == HttpMethod.POST || request.method() == HttpMethod.OPTIONS;
    }

    @Override
    public boolean process(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
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
  public boolean process(@NotNull String path, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context, @Nullable Map<String, Object> handlers) {
    if (!(path.isEmpty() || (path.length() == 1 && path.charAt(0) == '/') || path.equalsIgnoreCase("/rpc2"))) {
      return false;
    }

    if (request.method() != HttpMethod.POST) {
      return false;
    }

    ByteBuf content = request.content();
    if (content.readableBytes() == 0) {
      Responses.send(HttpResponseStatus.BAD_REQUEST, context.channel(), request);
      return true;
    }

    ByteBuf result;
    try (ByteBufInputStream in = new ByteBufInputStream(content)) {
      XmlRpcServerRequest xmlRpcServerRequest = new XmlRpcRequestProcessor().decodeRequest(in);
      if (StringUtil.isEmpty(xmlRpcServerRequest.getMethodName())) {
        LOG.warn("method name empty");
        return false;
      }

      Object response = invokeHandler(getHandler(xmlRpcServerRequest.getMethodName(), handlers == null ? handlerMapping : handlers), xmlRpcServerRequest);
      result = Unpooled.wrappedBuffer(new XmlRpcResponseProcessor().encodeResponse(response, CharsetToolkit.UTF8));
    }
    catch (SAXParseException e) {
      LOG.warn(e);
      Responses.send(HttpResponseStatus.BAD_REQUEST, context.channel(), request);
      return true;
    }
    catch (Throwable e) {
      context.channel().close();
      LOG.error(e);
      return true;
    }

    Responses.send(Responses.response("text/xml", result), context.channel(), request);
    return true;
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

    if (dot > -1) {
      throw new IllegalStateException("RPC handler object \"" + handlerName + "\" not found");
    }
    else {
      throw new IllegalStateException("RPC handler object not found for \"" + methodName);
    }
  }

  private static Object invokeHandler(@NotNull Object handler, XmlRpcServerRequest request) throws Throwable {
    return handler instanceof XmlRpcHandler ? (XmlRpcHandler)handler : invoke(handler, request.getMethodName(), request.getParameters());
  }

  private static Object invoke(Object target, String methodName, @SuppressWarnings("UseOfObsoleteCollectionType") Vector params) throws Throwable {
    Class<?> targetClass = (target instanceof Class) ? (Class) target : target.getClass();
    Class[] argClasses = null;
    Object[] argValues = null;
    if (params != null) {
      argClasses = new Class[params.size()];
      argValues = new Object[params.size()];
      for (int i = 0; i < params.size(); i++) {
        argValues[i] = params.elementAt(i);
        if (argValues[i] instanceof Integer) {
          argClasses[i] = Integer.TYPE;
        }
        else if (argValues[i] instanceof Double) {
          argClasses[i] = Double.TYPE;
        }
        else if (argValues[i] instanceof Boolean) {
          argClasses[i] = Boolean.TYPE;
        }
        else {
          argClasses[i] = argValues[i].getClass();
        }
      }
    }

    Method method;
    int dot = methodName.lastIndexOf('.');
    if (dot > -1 && dot + 1 < methodName.length()) {
      methodName = methodName.substring(dot + 1);
    }
    method = targetClass.getMethod(methodName, argClasses);

    // Our policy is to make all public methods callable except the ones defined in java.lang.Object
    if (method.getDeclaringClass() == Object.class) {
      throw new XmlRpcException(0, "Invoker can't call methods defined in java.lang.Object");
    }

    Object returnValue = method.invoke(target, argValues);
    if (returnValue == null && method.getReturnType() == Void.TYPE) {
      // Not supported by the spec.
      throw new IllegalArgumentException("void return types for handler methods not supported, " + methodName);
    }
    return returnValue;
  }
}