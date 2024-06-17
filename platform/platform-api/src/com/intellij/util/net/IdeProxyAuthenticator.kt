// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.net.Authenticator
import java.net.PasswordAuthentication

class IdeProxyAuthenticator(
  private val proxyAuth: ProxyAuthentication,
): Authenticator() {
  /**
   * JDK internals cache successful proxy authentications against the authenticator instance (or against null if the default authenticator is used).
   * Authentication cache is dropped if the proxy returns 407 and is re-requested again.
   * We want to achieve two goals here:
   * 1. if user has set the "remember" flag for credentials, they should be reused in the next IDE run;
   * 2. if the credentials do not suit proxy anymore, we must prompt the user again.
   * To fulfill the first point, the naive solution would be to provide remembered credentials once and then always prompt the user on every repeated request
   * (given the caching nature of the jdk's authenticator mechanism). But! If two threads simultaneously perform an http request through a proxy, JDK
   * will call this method twice since at the point of the request there is no auth data for both requests. We have to distinguish a repeated
   * auth request due to invalid credentials from just a concurrent request. And we have quite limited sources to do that. Two threads
   * may request the same URL, so the whole input of this method will be the same for both threads. I guess the only differentiator we can
   * have here is the thread itself, so a thread local is used.
   * Hash of the resulting credentials is used too, because consider the following scenario:
   * 1. both threads start the requests, both first don't use the proxy credentials, both get 407
   * 2. each of them calls this authenticator, it runs twice and returns known credentials both times
   * 3. both threads retry the requests, fail again with 407, ask this authenticator again
   * 4. we now prompt the user for credentials in the first thread
   * 5. the second thread comes and should reuse the prompt result of the first thread. So it should react to the fact that the credentials
   *    that were used before are already known to be wrong, and there are other available => no need to prompt again
   */
  private val lastRequest = ThreadLocal<String>()

  @Synchronized
  override fun getPasswordAuthentication(): PasswordAuthentication? {
    logger.debug { "$this on ${Thread.currentThread()}, lastRequest=${lastRequest.get()}" }
    // java.base/java/net/SocksSocketImpl.java:176 : there is SOCKS proxy auth, but without RequestorType passing
    val isProxy = RequestorType.PROXY == requestorType || "SOCKS authentication" == requestingPrompt
    if (!isProxy) {
      return null
    }
    val host = getHostNameReliably(requestingHost, requestingSite, requestingURL) ?: ""
    val port = requestingPort
    var credentials = proxyAuth.getOrPromptAuthentication(requestingPrompt, host, port)
    var key = getKey(credentials)
    if (lastRequest.get() == key) {
      credentials = proxyAuth.getPromptedAuthentication(requestingPrompt, host, port)
      key = getKey(credentials)
    }
    lastRequest.set(key)
    return credentials?.toPasswordAuthentication()
  }

  private fun getKey(credentials: Credentials?): String = "$this;c=${credentials.hash()}"

  override fun toString(): String = "Auth@${System.identityHashCode(this).toString(16)}{type=$requestorType, prompt=$requestingPrompt, " +
                                    "host=$requestingHost, port=$requestingPort, site=$requestingSite, url=$requestingURL}"

  private companion object {
    private val logger = logger<IdeProxyAuthenticator>()

    private fun Credentials.toPasswordAuthentication(): PasswordAuthentication? {
      if (userName == null) return null
      return PasswordAuthentication(userName!!, password?.toCharArray() ?: CharArray(0))
    }

    private fun Credentials?.hash(): Int {
      if (this == null) return 0
      var hash = 1 + (userName?.hashCode() ?: 0)
      hash = hash * 31 + (password?.hashCode() ?: 0)
      return hash
    }
  }
}