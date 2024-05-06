// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.isFulfilled
import java.net.Authenticator
import java.net.PasswordAuthentication

class IdeProxyAuthenticator(
  private val proxyAuth: ProxyAuthentication,
): Authenticator() {
  private val seenLocations = HashSet<String>()

  @Synchronized
  override fun getPasswordAuthentication(): PasswordAuthentication? {
    // java.base/java/net/SocksSocketImpl.java:176 : there is SOCKS proxy auth, but without RequestorType passing
    val isProxy = RequestorType.PROXY == requestorType || "SOCKS authentication" == requestingPrompt
    if (!isProxy) {
      return null
    }
    val host = getHostNameReliably(requestingHost, requestingSite, requestingURL) ?: ""
    val port = requestingPort
    val notSeenYet = seenLocations.add(locationKey(host, port))
    if (notSeenYet) {
      return proxyAuth.getOrPromptAuthentication(requestingPrompt, host, port)?.toPasswordAuthentication()
    }
    return proxyAuth.getPromptedAuthentication(requestingPrompt, host, port)?.toPasswordAuthentication()
  }

  override fun toString(): String = "Auth{type=$requestorType, prompt=$requestingPrompt, host=$requestingHost, " +
                                    "port=$requestingPort, site=$requestingSite, url=$requestingURL}"
}

private fun locationKey(host: String, port: Int) = "$host:$port"

private fun Credentials.toPasswordAuthentication(): PasswordAuthentication? {
  if (!isFulfilled()) return null
  return PasswordAuthentication(userName!!, password!!.toCharArray())
}