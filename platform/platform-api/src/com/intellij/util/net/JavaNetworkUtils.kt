// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net


internal object JavaNetworkUtils {
  const val HTTP_AUTH_TUNNELING_DISABLED_SCHEMES_PROPERTY: String = "jdk.http.auth.tunneling.disabledSchemes"

  const val BASIC_AUTH_SCHEME = "Basic"

  /**
   * See [sun.net.www.protocol.http.HttpURLConnection.disabledTunnelingSchemes]
   *
   * This check may give false positives if the user has changed the net.properties file
   */
  @JvmStatic
  fun isTunnelingAuthSchemeDisabled(scheme: String): Boolean {
    //val prop = NetProperties.get(HTTP_AUTH_TUNNELING_DISABLED_SCHEMES_PROPERTY) ?: "" // can't access non-exported sun.net.NetProperties
    // NetProperties takes default from (java.home)/conf/net.properties, for which the default state of the property is "Basic"
    val prop = System.getProperty(HTTP_AUTH_TUNNELING_DISABLED_SCHEMES_PROPERTY, BASIC_AUTH_SCHEME)
    return prop.contains(scheme, ignoreCase = true)
  }
}