// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.util.Url
import io.netty.bootstrap.Bootstrap
import org.jetbrains.annotations.ApiStatus
import java.net.URLConnection

abstract class BuiltInServerManager {
  companion object {
    @JvmStatic
    fun getInstance(): BuiltInServerManager = service()
  }

  abstract val port: Int

  /**
   * Set a port value for built-in server to use.
   *
   * @param port - [Integer] value define the port toi use for built-in server, or NULL to ignore overrides.
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  abstract fun overridePort(port: Int?)

  abstract val serverDisposable: Disposable?

  abstract fun createClientBootstrap(): Bootstrap

  abstract fun waitForStart(): BuiltInServerManager

  abstract fun isOnBuiltInWebServer(url: Url?): Boolean

  abstract fun configureRequestToWebServer(connection: URLConnection)

  abstract fun addAuthToken(url: Url): Url
}