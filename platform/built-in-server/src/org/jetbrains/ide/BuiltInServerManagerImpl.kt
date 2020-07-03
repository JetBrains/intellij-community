// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.idea.StartupUtil
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SystemProperties
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.serverBootstrap
import com.intellij.util.net.NetUtils
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Consumer

private const val PORTS_COUNT = 20
private const val PROPERTY_RPC_PORT = "rpc.port"
private const val PROPERTY_DISABLED = "idea.builtin.server.disabled"

private val LOG = logger<BuiltInServerManager>()

class BuiltInServerManagerImpl : BuiltInServerManager() {
  private var serverStartFuture: Future<*>? = null

  private var server: BuiltInServer? = null

  override val port: Int
    get() = if (server == null) defaultPort else server!!.port

  override val serverDisposable: Disposable?
    get() = server

  init {
    serverStartFuture = when {
      ApplicationManager.getApplication().isUnitTestMode -> null
      else -> startServerInPooledThread()
    }
  }

  override fun createClientBootstrap() = NettyUtil.nioClientBootstrap(server!!.eventLoopGroup)

  companion object {
    @JvmField
    internal val NOTIFICATION_GROUP: NotNullLazyValue<NotificationGroup> = object : NotNullLazyValue<NotificationGroup>() {
      override fun compute(): NotificationGroup {
        return NotificationGroup("Built-in Server", NotificationDisplayType.STICKY_BALLOON, true)
      }
    }

    @JvmStatic
    fun isOnBuiltInWebServerByAuthority(authority: String): Boolean {
      val portIndex = authority.indexOf(':')
      if (portIndex < 0 || portIndex == authority.length - 1) {
        return false
      }

      val port = StringUtil.parseInt(authority.substring(portIndex + 1), -1)
      if (port == -1) {
        return false
      }

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

  fun createServerBootstrap() = serverBootstrap(server!!.eventLoopGroup)

  override fun waitForStart(): BuiltInServerManager {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode ||
                   ApplicationManager.getApplication().isHeadlessEnvironment ||
                   !ApplicationManager.getApplication().isDispatchThread)

    var future: Future<*>?
    synchronized(this) {
      future = serverStartFuture
      if (future == null) {
        future = startServerInPooledThread()
        serverStartFuture = future
      }
    }

    future!!.get()
    return this
  }

  private fun startServerInPooledThread(): Future<*> {
    if (SystemProperties.getBooleanProperty(PROPERTY_DISABLED, false)) {
      return CompletableFuture<Any>().apply {
        completeExceptionally(Throwable("Built-in server is disabled by `$PROPERTY_DISABLED` VM option"))
      }
    }

    return StartupUtil.getServerFuture()
      .thenAcceptAsync(Consumer { mainServer ->
        try {
          @Suppress("DEPRECATION")
          server = when (mainServer) {
            null -> BuiltInServer.start(firstPort = defaultPort, portsCount = PORTS_COUNT)
            else -> BuiltInServer.start(eventLoopGroup = mainServer.eventLoopGroup, isEventLoopGroupOwner = false, firstPort = defaultPort,
                                        portsCount = PORTS_COUNT, tryAnyPort = true)
          }
          bindCustomPorts(server!!)
        }
        catch (e: Throwable) {
          LOG.info(e)
          NOTIFICATION_GROUP.value.createNotification(
            BuiltInServerBundle.message("notification.content.cannot.start.internal.http.server.git.integration.javascript.debugger.and.liveedit.may.operate.with.errors") +
            BuiltInServerBundle.message("notification.content.please.check.your.firewall.settings.and.restart") + ApplicationNamesInfo.getInstance().fullProductName,
            NotificationType.ERROR).notify(null)
          return@Consumer
        }

        LOG.info("built-in server started, port ${server!!.port}")
        Disposer.register(ApplicationManager.getApplication(), server!!)
      }, AppExecutorUtil.getAppExecutorService())
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