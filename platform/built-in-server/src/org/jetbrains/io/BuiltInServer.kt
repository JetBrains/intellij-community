// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.PlatformUtils
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.FastThreadLocalThread
import io.netty.util.concurrent.GenericFutureListener
import io.netty.util.internal.logging.InternalLoggerFactory
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@Internal
class BuiltInServer private constructor(
  val eventLoopGroup: EventLoopGroup,
  val childEventLoopGroup: EventLoopGroup,
  val port: Int,
  private val channelRegistrar: ChannelRegistrar,
) : Disposable {
  val isRunning: Boolean
    get() = !channelRegistrar.isEmpty

  companion object {
    private val selectorProvider: SelectorProvider? by lazy {
      try {
        val aClass = ClassLoader.getSystemClassLoader().loadClass("sun.nio.ch.DefaultSelectorProvider")
        MethodHandles.lookup()
          .findStatic(aClass, "create", MethodType.methodType(SelectorProvider::class.java))
          .invokeExact() as SelectorProvider
      }
      catch (_: Throwable) {
        null
      }
    }

    init {
      // IDEA-120811
      if (java.lang.Boolean.parseBoolean(System.getProperty("io.netty.random.id", "true"))) {
        System.setProperty("io.netty.machineId", "28:f0:76:ff:fe:16:65:0e")
        System.setProperty("io.netty.processId", Random.nextInt(65535).toString())
        System.setProperty("io.netty.allocator.type", "pooled")
      }

      System.setProperty("io.netty.serviceThreadPrefix", "Netty ")

      // https://youtrack.jetbrains.com/issue/IDEA-208908
      setSystemPropertyIfNotConfigured("io.netty.allocator.numDirectArenas", "1")
      setSystemPropertyIfNotConfigured("io.netty.allocator.numHeapArenas", "1")
      setSystemPropertyIfNotConfigured("io.netty.allocator.useCacheForAllThreads", "false")
      setSystemPropertyIfNotConfigured("io.netty.allocator.cacheTrimIntervalMillis", "600000")

      val logger = IdeaNettyLogger()
      InternalLoggerFactory.setDefaultFactory(object : InternalLoggerFactory() {
        override fun newInstance(name: String) = logger
      })
    }

    private fun setSystemPropertyIfNotConfigured(name: String, @Suppress("SameParameterValue") value: String) {
      if (System.getProperty(name) == null) {
        System.setProperty(name, value)
      }
    }

    suspend fun start(firstPort: Int, portsCount: Int, tryAnyPort: Boolean, handler: (() -> ChannelHandler)? = null): BuiltInServer {
      val provider = selectorProvider ?: SelectorProvider.provider()
      val factory = BuiltInServerThreadFactory()
      val eventLoopGroup = MultiThreadIoEventLoopGroup(if (PlatformUtils.isIdeaCommunity()) 2 else 3, factory, NioIoHandler.newFactory(provider))
      val channelRegistrar = ChannelRegistrar()
      val bootstrap = createServerBootstrap(eventLoopGroup, eventLoopGroup)
      configureChildHandler(bootstrap, channelRegistrar, handler)
      val port = bind(firstPort, portsCount, tryAnyPort, bootstrap, channelRegistrar)
      return BuiltInServer(eventLoopGroup, eventLoopGroup, port, channelRegistrar)
    }

    fun configureChildHandler(
      bootstrap: ServerBootstrap,
      channelRegistrar: ChannelRegistrar,
      channelHandler: (() -> ChannelHandler)? = null,
    ) {
      val portUnificationServerHandler = if (channelHandler == null) PortUnificationServerHandler() else null
      bootstrap.childHandler(object : ChannelInitializer<Channel>(), ChannelHandler {
        override fun initChannel(channel: Channel) {
          channel.pipeline().addLast(channelRegistrar, channelHandler?.invoke() ?: portUnificationServerHandler)
        }
      })
    }

    fun replaceDefaultHandler(context: ChannelHandlerContext, channelHandler: ChannelHandler) {
      context.pipeline().replace(DelegatingHttpRequestHandler::class.java, "replacedDefaultHandler", channelHandler)
    }

    private suspend fun bind(
      firstPort: Int,
      portsCount: Int,
      tryAnyPort: Boolean,
      bootstrap: ServerBootstrap,
      registrar: ChannelRegistrar,
    ): Int {
      val address = InetAddress.getByName("127.0.0.1")
      val maxPort = (firstPort + portsCount) - 1
      for (port in firstPort..maxPort) {
        // some antiviral software detects viruses by the fact of accessing these ports, so we should not touch them to appear innocent
        if (port == 6953 || port == 6969 || port == 6970) {
          continue
        }

        val future = asDeferred(bootstrap.bind(address, port)).await()
        if (future.isSuccess) {
          registrar.setServerChannel(future.channel(), true)
          return port
        }
        else if (!tryAnyPort && port == maxPort) {
          throw future.cause()
        }
      }

      logger<BuiltInServer>().info("Cannot bind to our default range, so, try to bind to any free port")
      val future = asDeferred(bootstrap.bind(address, 0)).await()
      if (future.isSuccess) {
        registrar.setServerChannel(future.channel(), true)
        return (future.channel().localAddress() as InetSocketAddress).port
      }
      throw future.cause()
    }

    private fun asDeferred(channelFuture: ChannelFuture): CompletableDeferred<ChannelFuture> {
      val deferred = CompletableDeferred<ChannelFuture>()
      channelFuture.addListener(object : GenericFutureListener<ChannelFuture> {
        override fun operationComplete(future: ChannelFuture) {
          try {
            channelFuture.removeListener(this)
          }
          finally {
            deferred.complete(future)
          }
        }
      })
      return deferred
    }

    private fun createServerBootstrap(parentEventLoopGroup: EventLoopGroup, childEventLoopGroup: EventLoopGroup): ServerBootstrap =
      ServerBootstrap()
        .group(parentEventLoopGroup, childEventLoopGroup)
        .channel(NioServerSocketChannel::class.java)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .childOption(ChannelOption.SO_KEEPALIVE, true)
  }

  fun createServerBootstrap(): ServerBootstrap = createServerBootstrap(eventLoopGroup, childEventLoopGroup)

  override fun dispose() {
    channelRegistrar.close()
    logger<BuiltInServer>().info("web server stopped")
  }
}

private class BuiltInServerThreadFactory : ThreadFactory {
  private val counter = AtomicInteger()

  override fun newThread(r: Runnable): Thread = FastThreadLocalThread(r, "Netty Builtin Server ${counter.incrementAndGet()}")
}
