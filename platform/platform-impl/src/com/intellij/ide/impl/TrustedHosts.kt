// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.URLUtil
import com.intellij.util.io.URLUtil.SCHEME_SEPARATOR
import com.intellij.util.io.isAncestor
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

@NlsSafe
fun getProjectOriginUrl(projectDir: Path?): String? {
  if (projectDir == null) return null
  val epName = ExtensionPointName.create<ProjectOriginInfoProvider>("com.intellij.projectOriginInfoProvider")
  for (extension in epName.extensions) {
    val url = extension.getOriginUrl(projectDir)
    if (url != null) {
      return url
    }
  }
  return null
}

private val KNOWN_HOSTINGS = listOf(
  "git.jetbrains.space",
  "github.com",
  "bitbucket.org",
  "gitlab.com")

@VisibleForTesting
data class Origin(val protocol: String?, val host: String)

@VisibleForTesting
fun getOriginFromUrl(url: String): Origin? {
  try {
    val urlWithScheme = if (URLUtil.containsScheme(url)) url else "ssh://$url"
    val uri = URI(urlWithScheme)
    var host = uri.host

    if (host == null) {
      if (uri.scheme == "ssh") {
        if (uri.authority != null) {
          // git@github.com:JetBrains
          val at = uri.authority.split("@")
          val hostAndOrg = if (at.size > 1) at[1] else at[0]
          val comma = hostAndOrg.split(":")
          host = comma[0]
          if (comma.size > 1) {
            val org = comma[1]
            return Origin(uri.scheme, "$host/$org")
          }
        }
      }
    }

    if (host == null) return null

    if (KNOWN_HOSTINGS.contains(host)) {
      val path = uri.path
      val secondSlash = path.indexOf("/", 1) // path always starts with '/'
      val organization = if (secondSlash >= 0) path.substring(0, secondSlash) else path
      return Origin(uri.scheme, uri.host + organization)
    }
    return Origin(uri.scheme, uri.host)
  }
  catch (e: Exception) {
    LOG.warn("Couldn't parse URL $url", e)
  }
  return null
}

@State(name = "Trusted.Paths.Settings", storages = [Storage("trusted-paths.xml")])
@Service(Service.Level.APP)
internal class TrustedPathsSettings : SimplePersistentStateComponent<TrustedPathsSettings.State>(State()) {

  class State : BaseState() {
    @get:OptionTag("TRUSTED_PATHS")
    var trustedPaths by list<String>()
  }

  fun isPathTrusted(path: Path): Boolean {
    return state.trustedPaths.map { Paths.get(it) }.any { it.isAncestor(path) }
  }

  fun getTrustedPaths(): List<String> = Collections.unmodifiableList(state.trustedPaths)

  fun setTrustedPaths(paths: List<String>) {
    state.trustedPaths = ArrayList<String>(paths)
  }

  fun addTrustedPath(path: String) {
    state.trustedPaths.add(path)
  }
}

private val LOG = Logger.getInstance("com.intellij.ide.impl.TrustedHosts")
