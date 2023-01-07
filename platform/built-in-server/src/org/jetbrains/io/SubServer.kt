// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.net.loopbackSocketAddress
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.CustomPortServerManager
import org.jetbrains.ide.XmlRpcServerImpl
import java.net.InetSocketAddress

internal class SubServer(private val user: CustomPortServerManager,
                         private val server: BuiltInServer) : CustomPortServerManager.CustomPortService, Disposable {
  private var channelRegistrar: ChannelRegistrar? = null

  override val isBound: Boolean
    get() = channelRegistrar != null && !channelRegistrar!!.isEmpty

  init {
    user.setManager(this)
  }

  fun bind(port: Int): Boolean {
    if (port == server.port || port == -1) {
      return true
    }

    if (channelRegistrar == null) {
      Disposer.register(server, this)
      channelRegistrar = ChannelRegistrar()
    }

    val bootstrap = server.createServerBootstrap()
    val xmlRpcHandlers = user.createXmlRpcHandlers()
    if (xmlRpcHandlers == null) {
      BuiltInServer.configureChildHandler(bootstrap, channelRegistrar!!, null)
    }
    else {
      val handler = XmlRpcDelegatingHttpRequestHandler(xmlRpcHandlers)
      bootstrap.childHandler(object : ChannelInitializer<Channel>() {
        override fun initChannel(channel: Channel) {
          channel.pipeline().addLast(channelRegistrar!!)
          NettyUtil.addHttpServerCodec(channel.pipeline())
          channel.pipeline().addLast(handler)
        }
      })
    }

    try {
      bootstrap.localAddress(if (user.isAvailableExternally) InetSocketAddress(port) else loopbackSocketAddress(port))
      channelRegistrar!!.setServerChannel(bootstrap.bind().syncUninterruptibly().channel(), false)
      return true
    }
    catch (e: Exception) {
      try {
        NettyUtil.log(e, Logger.getInstance(BuiltInServer::class.java))
      }
      finally {
        user.cannotBind(e, port)
      }
      return false
    }
  }

  private fun stop() {
    channelRegistrar?.close()
  }

  override fun rebind(): Boolean {
    stop()
    return bind(user.port)
  }

  override fun dispose() {
    stop()
    user.setManager(null)
  }
}

@ChannelHandler.Sharable
private class XmlRpcDelegatingHttpRequestHandler(private val handlers: Map<String, Any>) : DelegatingHttpRequestHandlerBase() {
  override fun process(context: ChannelHandlerContext, request: FullHttpRequest, urlDecoder: QueryStringDecoder): Boolean {
    if (handlers.isEmpty()) {
      // not yet initialized, for example, P2PTransport could add handlers after we bound.
      return false
    }

    return request.method() == HttpMethod.POST && XmlRpcServerImpl.getInstance().process(urlDecoder.path(), request, context, handlers)
  }
}