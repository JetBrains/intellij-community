// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import java.net.Authenticator
import java.net.InetAddress
import java.net.PasswordAuthentication
import java.net.URL

private val LOG = logger<IdeProxyAuthenticator>()

/**
 * **If someone decides to change this implementation, keep this in mind**
 * * For _basic_ authentication scheme, the JRE caches successful proxy authentications against the authenticator instance
 * (or against null if the default authenticator is used). So for later requests cached credentials will be used by JDK automatically without
 * calling [getPasswordAuthentication] again. Authentication cache is dropped if the proxy returns 407 and is re-requested again.
 * * For _SOCKS_, if auth is required, [getPasswordAuthentication] is called for every connection: [java.net.SocksSocketImpl.authenticate].
 * * Authentication cache that works for _basic_ scheme does not work for _NTLM_ or _Kerberos_. The observed behavior is that
 * [getPasswordAuthentication] is called for every connection. Probably this happens due to multistage auth.
 *
 * This has the following consequences:
 * It is not possible by the means of [Authenticator] API to distinguish the reason of authentication request in general case:
 * is it because previous credentials that were valid turned invalid, and we are called second+ time for the same original request,
 * or because the underlying infrastructure just calls [getPasswordAuthentication] for every connection?
 * In turn, it means that in general we can't really tell if we need to ask the user to provide new credentials (maybe they made a typo in password).
 *
 * It does not seem possible to work around this issue robustly on the [Authenticator] level.
 *
 * That said, the current implementation returns any already known credentials for every request.
 * The only help the user can get is the "Check connection" button in the proxy settings page...
 *
 * @see ProxyAuthentication
 */
@ApiStatus.Internal
class IdeProxyAuthenticator(private val proxyAuth: ProxyAuthentication): Authenticator() {
  override fun getPasswordAuthentication(): PasswordAuthentication? = requestPasswordAuthentication(
    requestingHost, requestingSite, requestingPort, requestingProtocol, requestingPrompt, requestingScheme, requestingURL, requestorType
  )

  override fun requestPasswordAuthenticationInstance(
    host: String?,
    addr: InetAddress?,
    port: Int,
    protocol: String?,
    prompt: @NlsSafe String,
    scheme: String?,
    url: URL?,
    reqType: RequestorType,
  ): PasswordAuthentication? {
    LOG.debug {
      "Auth@${System.identityHashCode(this).toString(16)}{type=$reqType, scheme=$scheme, protocol=$protocol, prompt=$prompt," +
      " host=$host, port=$port, site=$addr, url=$url} on ${Thread.currentThread()}"
    }

    // java.net.SocksSocketImpl#authenticate : there is SOCKS proxy auth, but without RequestorType passing
    val isProxy = RequestorType.PROXY == reqType || "SOCKS authentication" == prompt
    if (!isProxy) return null

    val host = getHostNameReliably(host, addr, url) ?: ""

    var credentials = proxyAuth.getKnownAuthentication(host, port)
    if (credentials == null || credentials.userName == null || credentials.password == null) {
      // TODO condition for password exists because HttpConfigurable does not drop PROXY_AUTHENTICATION flag when it erases the password...
      //  Historically empty passwords were not supported anyway...
      credentials = proxyAuth.getPromptedAuthentication(prompt, host, port)
    }
    if (credentials != null && credentials.userName != null) {
      return PasswordAuthentication(credentials.userName!!, credentials.password?.toCharArray() ?: CharArray(0))
    }

    return null
  }
}
