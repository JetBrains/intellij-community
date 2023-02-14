// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.Url
import io.netty.bootstrap.Bootstrap
import org.jetbrains.annotations.ApiStatus

import java.net.URLConnection

abstract class BuiltInServerManager {
  companion object {
    @JvmStatic
    fun getInstance(): BuiltInServerManager = ApplicationManager.getApplication().getService(BuiltInServerManager::class.java)
  }

  abstract val port: Int

  /**
   * Set a port value for built-in server to used.
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