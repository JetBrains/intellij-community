// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.auth

import com.intellij.ide.plugins.CustomPluginRepositoryService
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.NotNull
import java.util.*

/**
 * Collects custom auth headers from EPs, validates them and reacts to authorisation status changes.
 *
 * This service keeps track of [PluginHostAuthContributor] injected headers to ensure that:
 * - each contributor handles only 1 url
 * - each url has no more than 1 contributor
 */
@Service(Service.Level.APP)
class PluginHostAuthService {

  private val contributorToUrlCache = Collections.synchronizedMap(WeakHashMap<PluginHostAuthContributor, String>())

  val connectionTuner = HttpRequests.ConnectionTuner { connection ->
    try {
      getAllCustomHeaders(connection.url.toString())
        .forEach { (k, v) -> connection.addRequestProperty(k, v) }
    } catch (e: Exception) {
      logger.error(e)
    }
  }

  @NotNull
  fun getAllCustomHeaders(@NotNull url: String): Map<String, String> {
    val allContributors = PluginHostAuthContributor.EP_NAME.extensionsIfPointIsRegistered

    if (allContributors.isEmpty())
      return emptyMap()
    if (!hasNoOrSingleContributor(url, allContributors))
      return cancelWithWarning("Multiple contributors found for url: $url")
    if (allContributors.any { !handlesSingleUrl(it, url) })
      return cancelWithWarning("Contributor ${allContributors.find { !handlesSingleUrl(it, url) }} tried to inject into multiple urls")

    val matchingContributor = allContributors.find { it.canHandleSafe(url) }
    return matchingContributor
             ?.also { contributor -> updateCaches(url, contributor) }
             ?.getCustomHeadersSafe()
           ?: emptyMap()
  }


  fun authorizationChanged() {
    CustomPluginRepositoryService.getInstance()?.clearCache()
  }

  private fun hasNoOrSingleContributor(@NotNull url: String, @NotNull contributors: List<PluginHostAuthContributor>): Boolean {
    return contributors.count { it.canHandleSafe(url) } <= 1
  }

  private fun handlesSingleUrl(@NotNull contributor: PluginHostAuthContributor, @NotNull url: String): Boolean {
    val lastKnownUrl = contributorToUrlCache[contributor]
    return url == lastKnownUrl || lastKnownUrl == null
  }


  private fun updateCaches(@NotNull url: String, @NotNull contributor: PluginHostAuthContributor) {
    contributorToUrlCache[contributor] = url
  }

  @NotNull
  private fun cancelWithWarning(message: String): Map<String, String> {
    logger.warn(message)
    return emptyMap()
  }

  companion object {
    val logger = thisLogger()

    fun PluginHostAuthContributor.canHandleSafe(url: String): Boolean =
      withLogging(false) { canHandle(url) }
    fun PluginHostAuthContributor.getCustomHeadersSafe(): Map<String, String> =
      withLogging(emptyMap()) { getCustomHeaders() }

    private inline fun <T> withLogging(default: T, f: () -> T): T = try {
      f()
    } catch (e: Exception) {
      logger.error(e)
      default
    }

  }
}