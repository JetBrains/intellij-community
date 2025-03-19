// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.credentialStore.Credentials
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import java.net.Authenticator
import java.net.PasswordAuthentication


/**
 * **If someone decides to change this implementation, keep this in mind**
 * * For _basic_ authentication scheme JDK internals cache successful proxy authentications against the authenticator instance
 * (or against null if the default authenticator is used). So for later requests cached credentials will be used by JDK automatically without
 * calling [getPasswordAuthentication] again. Authentication cache is dropped if the proxy returns 407 and is re-requested again.
 * * For _SOCKS_, if auth is required, [getPasswordAuthentication] is called for every connection: [java.net.SocksSocketImpl.authenticate].
 * * Authentication cache that works for _basic_ scheme does not work for _NTLM_ or _Kerberos_. The observed behavior is that
 * [getPasswordAuthentication] is called for every connection. Probably this happens due to multistage auth.
 *
 * This has the following consequences:
 * It is not possible by the means of [Authenticator] API to distinguish the reason of authentication request in general case:
 * is it because previous credentials that were valid turned invalid and we are called second+ time for the same original request,
 * or because the underlying infrastructure just calls [getPasswordAuthentication] for every connection?
 * In turn, it means that in general we can't really tell if we need to ask the user to provide new credentials (maybe they made a typo in password).
 *
 * It does not seem possible to workaround this issue robustly on [Authenticator] level.
 *
 * That said, the current implementation returns any already known credentials for every request.
 * The only help the user can get is the "Check connection" button in the proxy settings page...
 *
 * @see ProxyAuthentication
 */
class IdeProxyAuthenticator(
  private val proxyAuth: ProxyAuthentication,
): Authenticator() {

  @Synchronized
  override fun getPasswordAuthentication(): PasswordAuthentication? {
    logger.debug { "$this on ${Thread.currentThread()}" }
    // java.base/java/net/SocksSocketImpl.java:176 : there is SOCKS proxy auth, but without RequestorType passing
    val isProxy = RequestorType.PROXY == requestorType || "SOCKS authentication" == requestingPrompt
    if (!isProxy) {
      return null
    }
    val host = getHostNameReliably(requestingHost, requestingSite, requestingURL) ?: "" // TODO remove requestingURL, it is not relevant for proxy _auth_
    val port = requestingPort

    val credentials = proxyAuth.getKnownAuthentication(host, port)
    if (credentials?.userName != null && credentials.password != null) {
      // TODO condition for password exists because HttpConfigurable does not drop PROXY_AUTHENTICATION flag when it erases the password...
      //  Historically empty passwords were not supported anyway...
      return credentials.toPasswordAuthentication()
    }
    return proxyAuth.getPromptedAuthentication(requestingPrompt, host, port)?.toPasswordAuthentication()
  }

  override fun toString(): String = "Auth@${System.identityHashCode(this).toString(16)}{" +
                                    "type=$requestorType, scheme=$requestingScheme, protocol=$requestingProtocol, " +
                                    "prompt=$requestingPrompt, host=$requestingHost, port=$requestingPort, " +
                                    "site=$requestingSite, url=$requestingURL}"

  private companion object {
    private val logger = logger<IdeProxyAuthenticator>()

    private fun Credentials.toPasswordAuthentication(): PasswordAuthentication? {
      if (userName == null) return null
      return PasswordAuthentication(userName!!, password?.toCharArray() ?: CharArray(0))
    }
  }
}