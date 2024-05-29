// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net

import java.net.Authenticator
import java.net.ProxySelector

internal object PlatformJdkProxyProvider {
  val proxySelector: ProxySelector by lazy { IdeProxySelector(ProxySettings.getInstance().asConfigurationProvider()) }
  val authenticator: Authenticator by lazy { IdeProxyAuthenticator(ProxyAuthentication.getInstance()) }
}
