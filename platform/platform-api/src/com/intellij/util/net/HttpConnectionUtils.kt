// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection

/**
 * Uses platform network settings (e.g., [ProxySettings]).
 */
object HttpConnectionUtils {
  /**
   * Tests http connection to the host (through proxy if configured).
   * @see [JdkProxyProvider]
   */
  @Throws(IOException::class)
  @JvmStatic
  fun prepareUrl(url: String) {
    val connection = openConnection(url)
    try {
      connection.connect()
      connection.getInputStream()
    }
    catch (e: IOException) {
      throw e
    }
    catch (_: Throwable) { }
    finally {
      if (connection is HttpURLConnection) {
        connection.disconnect()
      }
    }
  }

  /**
   * Opens a connection to a given location using configured http proxy settings.
   * @see [JdkProxyProvider]
   */
  @Throws(IOException::class)
  @JvmStatic
  fun openConnection(location: String): URLConnection {
    val url = URL(location)
    var urlConnection: URLConnection? = null
    val proxies = JdkProxyProvider.getInstance().proxySelector.select(VfsUtil.toUri(url.toString()))
    if (proxies.isEmpty()) {
      urlConnection = url.openConnection()
    }
    else {
      var exception: IOException? = null
      for (proxy in proxies) {
        try {
          urlConnection = url.openConnection(proxy)
          break
        }
        catch (e: IOException) {
          // continue iteration
          exception = e
        }
      }
      if (urlConnection == null && exception != null) {
        throw exception
      }
    }
    check(urlConnection != null)
    urlConnection.setReadTimeout(HttpRequests.READ_TIMEOUT)
    urlConnection.setConnectTimeout(HttpRequests.CONNECTION_TIMEOUT)
    return urlConnection
  }

  /**
   * Opens HTTP connection to a given location using configured http proxy settings.
   * @param location url to connect to
   * @throws IOException in case of any I/O troubles or if created connection isn't an instance of HttpURLConnection.
   * @see [JdkProxyProvider]
   */
  @Throws(IOException::class)
  @JvmStatic
  fun openHttpConnection(location: String): HttpURLConnection {
    val urlConnection = openConnection(location)
    if (urlConnection is HttpURLConnection) {
      return urlConnection
    }
    throw IOException("Expected ${HttpURLConnection::class}, but got ${urlConnection::class}")
  }
}