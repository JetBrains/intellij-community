/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.apache.xmlrpc.*;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jetbrains.ide.WebServerManager;
import org.jetbrains.io.Responses;

@ChannelHandler.Sharable
public class XmlRpcServerImpl extends SimpleChannelUpstreamHandler implements XmlRpcServer, Consumer<ChannelPipeline> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.XmlRpcServerImpl");

  private final DefaultHandlerMapping handlerMapping = new DefaultHandlerMapping();
  // idea doesn't use authentication
  private final XmlRpcContext xmlRpcContext = new DefaultXmlRpcContext(null, null, handlerMapping);

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
  }

  @Override
  public void consume(ChannelPipeline pipeline) {
    pipeline.addLast("pluggable_xmlRpc", this);
  }

  public int getPortNumber() {
    return WebServerManager.getInstance().getPort();
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
          result = ChannelBuffers.copiedBuffer(new XmlRpcWorker(xmlRpcContext.getHandlerMapping()).execute(in, xmlRpcContext));
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
}