// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.NotNull

/**
 * Collects custom auth headers from EPs and optionally provides a HttpRequests.ConnectionTuner with injected headers
 */
@Service(Service.Level.APP)
class PluginRepositoryAuthService {

  val connectionTuner: HttpRequests.ConnectionTuner = HttpRequests.ConnectionTuner { connection ->
    try {
      getAllCustomHeaders(connection.url.toString())
        .forEach { (k, v) -> connection.addRequestProperty(k, v) }
    }
    catch (e: Exception) {
      LOG.warn("Filed to inject headers into request(${connection.url})", e)
    }
  }

  @NotNull
  fun getAllCustomHeaders(@NotNull url: String): Map<String, String> {
    val allContributors = PluginRepositoryAuthProvider.EP_NAME.extensionsIfPointIsRegistered
    val matchingContributors = allContributors.filter { it.canHandleSafe(url) }

    if (matchingContributors.isEmpty())
      return emptyMap()
    if (matchingContributors.size > 1)
      LOG.warn("Multiple contributors tried to inject headers in to url($url): ${matchingContributors.joinToString { it.javaClass.simpleName }}")

    val primeCandidate = matchingContributors.first()

    return primeCandidate.getCustomHeadersSafe(url)
  }
}

private val LOG = logger<PluginRepositoryAuthService>()

private fun PluginRepositoryAuthProvider.canHandleSafe(url: String): Boolean = try {
    canHandle(url)
  } catch (e: Exception) {
    LOG.warn("Error while checking if a provider can handle URL($url), assuming false", e)
    false
  }

private fun PluginRepositoryAuthProvider.getCustomHeadersSafe(url: String): Map<String, String> {
  return try {
    getAuthHeaders(url)
  } catch (e: Exception) {
    LOG.warn("Failed to get custom headers from provider for URL($url), returning emptyMap()", e)
    emptyMap()
  }
}