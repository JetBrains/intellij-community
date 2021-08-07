// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.net.URISyntaxException

private val LOG = logger<JBProtocolNavigateCommand>()

@VisibleForTesting
fun areOriginsEqual(originUrl: String?, projectOriginUrl: String?): Boolean {
  if (originUrl.isNullOrBlank() || projectOriginUrl.isNullOrBlank()) return false

  val canonicalOrigin = extractCanonicalPath(originUrl)
  val canonicalProjectOrigin = extractCanonicalPath(projectOriginUrl)
  return canonicalOrigin != null && canonicalProjectOrigin == canonicalOrigin
}

private fun extractCanonicalPath(originUrl: String?) : String? {
  if (originUrl.isNullOrBlank()) return null
  try {
    val hostAndPath = extractHostAndPath(originUrl)
    return hostAndPath.toLowerCase().removeSuffix(".git")
  } catch(e: URISyntaxException) {
    LOG.warn("Malformed origin url '$originUrl' in navigate request", e)
    return null
  }
}

private fun extractHostAndPath(url: String) : String {
  val urlWithScheme = if (URLUtil.containsScheme(url)) url else "ssh://$url"
  val uri = URI(urlWithScheme)
  val host = uri.host
  val path = uri.path ?: ""

  //val host, path
  if (host != null) {
    val hostPort = uri.host + (if (uri.port != -1) ":${uri.port}" else "")
    return hostPort + path
  }

  if (uri.scheme == "ssh") {
    if (uri.authority != null) {
      // git@github.com:JetBrains
      val at = uri.authority.split("@")
      val hostAndOrg = if (at.size > 1) at[1] else at[0]
      val comma = hostAndOrg.split(":")
      val sshPath = if (comma.size > 1) "/" + comma[1] + path else path
      return comma[0] + sshPath
    }
  }

  return ""
}