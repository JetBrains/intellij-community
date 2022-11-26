// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.idea.getServerFutureAsync
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.SystemProperties
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.net.NetUtils
import io.netty.bootstrap.ServerBootstrap
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import org.jetbrains.builtInWebServer.TOKEN_HEADER_NAME
import org.jetbrains.builtInWebServer.TOKEN_PARAM_NAME
import org.jetbrains.builtInWebServer.acquireToken
import org.jetbrains.io.BuiltInServer
import org.jetbrains.io.NettyUtil
import org.jetbrains.io.SubServer
import java.io.IOException
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URLConnection
import java.util.*

private const val PORTS_COUNT = 20
private const val PROPERTY_RPC_PORT = "rpc.port"
private const val PROPERTY_DISABLED = "idea.builtin.server.disabled"

private val LOG = logger<BuiltInServerManager>()

class BuiltInServerManagerImpl : BuiltInServerManager() {
  private var serverStartFuture: Job? = null
  private var server: BuiltInServer? = null
  private var portOverride: Int? = null

  override val port: Int
    get() = portOverride ?: server?.port ?: defaultPort

  override val serverDisposable: Disposable?
    get() = server

  init {
    val app = ApplicationManager.getApplication()
    serverStartFuture = when {
      app.isUnitTestMode -> null
      else -> app.coroutineScope.async(Dispatchers.IO) { startServerInPooledThread() }
    }
  }

  override fun createClientBootstrap() = NettyUtil.nioClientBootstrap(server!!.childEventLoopGroup)

  companion object {
    internal const val NOTIFICATION_GROUP = "Built-in Server"

    @JvmStatic
    fun isOnBuiltInWebServerByAuthority(authority: String): Boolean {
      val portIndex = authority.indexOf(':')
      if (portIndex < 0 || portIndex == authority.length - 1) {
        return false
      }

      val port = authority.substring(portIndex + 1).toIntOrNull() ?: return false
      val options = BuiltInServerOptions.getInstance()
      val idePort = getInstance().port
      if (options.builtInServerPort != port && idePort != port) {
        return false
      }

      val host = authority.substring(0, portIndex)
      if (NetUtils.isLocalhost(host)) {
        return true
      }

      try {
        val inetAddress = InetAddress.getByName(host)
        return inetAddress.isLoopbackAddress ||
               inetAddress.isAnyLocalAddress ||
               options.builtInServerAvailableExternally && idePort != port && NetworkInterface.getByInetAddress(inetAddress) != null
      }
      catch (e: IOException) {
        return false
      }
    }
  }

  fun createServerBootstrap(): ServerBootstrap = server!!.createServerBootstrap()

  override fun waitForStart(): BuiltInServerManager {
    val app = ApplicationManager.getApplication()
    LOG.assertTrue(app.isUnitTestMode ||
                   app.isHeadlessEnvironment ||
                   !app.isDispatchThread,
                   "Should not wait for built-in server on EDT")

    var future: Job?
    synchronized(this) {
      future = serverStartFuture
      if (future == null) {
        future = app.coroutineScope.async(Dispatchers.IO) { startServerInPooledThread() }
        serverStartFuture = future
      }
    }

    future!!.asCompletableFuture().join()
    return this
  }

  @OptIn(DelicateCoroutinesApi::class)
  private suspend fun startServerInPooledThread() {
    if (SystemProperties.getBooleanProperty(PROPERTY_DISABLED, false)) {
      throw RuntimeException("Built-in server is disabled by `$PROPERTY_DISABLED` VM option")
    }

    val mainServer = getServerFutureAsync().await()
    try {
      server = if (mainServer == null) {
        BuiltInServer.start(firstPort = defaultPort, portsCount = PORTS_COUNT, tryAnyPort = true)
      }
      else {
        BuiltInServer.start(parentEventLoopGroup = mainServer.eventLoopGroup,
                            childEventLoopGroup = mainServer.childEventLoopGroup,
                            isEventLoopGroupOwner = false,
                            firstPort = defaultPort,
                            portsCount = PORTS_COUNT,
                            tryAnyPort = true)
      }

      bindCustomPorts(server!!)
    }
    catch (e: Throwable) {
      LOG.info(e)
      val message = BuiltInServerBundle.message("notification.content.cannot.start.internal.http.server.and.ask.for.restart.0",
                                                ApplicationNamesInfo.getInstance().fullProductName)
      Notification(NOTIFICATION_GROUP, message, NotificationType.ERROR).notify(null)
      return
    }

    LOG.info("built-in server started, port ${server!!.port}")
    Disposer.register(ApplicationManager.getApplication(), server!!)
  }

  override fun isOnBuiltInWebServer(url: Url?): Boolean {
    return url != null && !url.authority.isNullOrEmpty() && isOnBuiltInWebServerByAuthority(url.authority!!)
  }

  override fun addAuthToken(url: Url): Url {
    return when {
      // built-in server url contains query only if token specified
      url.parameters != null -> url
      else -> Urls.newUrl(url.scheme!!, url.authority!!, url.path, Collections.singletonMap(TOKEN_PARAM_NAME, acquireToken()))
    }
  }

  override fun overridePort(port: Int?) {
    if (port != this.port) {
      portOverride = port
    }
  }

  override fun configureRequestToWebServer(connection: URLConnection) {
    connection.setRequestProperty(TOKEN_HEADER_NAME, acquireToken())
  }
}

// Default port will be occupied by main idea instance - define the custom default to avoid searching of free port
private val defaultPort: Int
  get() = SystemProperties.getIntProperty(PROPERTY_RPC_PORT, if (ApplicationManager.getApplication().isUnitTestMode) 64463 else BuiltInServerOptions.DEFAULT_PORT)

private fun bindCustomPorts(server: BuiltInServer) {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return
  }

  CustomPortServerManager.EP_NAME.forEachExtensionSafe { customPortServerManager ->
    SubServer(customPortServerManager, server).bind(customPortServerManager.port)
  }
}
