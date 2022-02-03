// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.NotNull
import java.net.URI
import java.util.*

/**
 * Collects custom auth headers from EPs and validates them
 *
 * This service keeps track of [PluginRepositoryAuthProvider] injected headers to ensure that:
 * - each contributor handles only 1 domain
 * - each domain has no more than 1 contributor
 */
@Service(Service.Level.APP)
class PluginRepositoryAuthService {
  private val contributorToDomainCache = Collections.synchronizedMap(WeakHashMap<PluginRepositoryAuthProvider, String>())

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
    val allContributors = PluginRepositoryAuthProvider.EP_NAME.extensionsIfPointIsRegistered

    val domain = getDomainFromUrl(url) ?: return cancelWithWarning("Can't get domain from url: $url")

    if (allContributors.isEmpty())
      return emptyMap()
    if (!hasNoOrSingleContributor(domain, allContributors))
      return cancelWithWarning("Multiple contributors found for domain: $domain")
    if (allContributors.any { !handlesSingleDomain(it, domain) })
      return cancelWithWarning("Contributor ${allContributors.find { !handlesSingleDomain(it, domain) }} tried to inject into multiple domains")

    val matchingContributor = allContributors.find { it.canHandleSafe(domain) }
    return matchingContributor
             ?.also { contributor -> updateCaches(domain, contributor) }
             ?.getCustomHeadersSafe(url)
           ?: emptyMap()
  }

  private fun hasNoOrSingleContributor(@NotNull domain: String, @NotNull contributors: List<PluginRepositoryAuthProvider>): Boolean {
    return contributors.count { it.canHandleSafe(domain) } <= 1
  }

  private fun handlesSingleDomain(@NotNull contributor: PluginRepositoryAuthProvider, @NotNull domain: String): Boolean {
    val lastKnownDomain = contributorToDomainCache[contributor]
    return domain == lastKnownDomain || lastKnownDomain == null
  }

  private fun getDomainFromUrl(url: String): String? = URI(url).host

  private fun updateCaches(@NotNull url: String, @NotNull contributor: PluginRepositoryAuthProvider) {
    contributorToDomainCache[contributor] = url
  }

  @NotNull
  private fun cancelWithWarning(message: String): Map<String, String> {
    logger.warn(message)
    return emptyMap()
  }

  companion object {
    val logger = thisLogger()

    fun PluginRepositoryAuthProvider.canHandleSafe(domain: String): Boolean =
      withLogging(false) { canHandle(domain) }
    fun PluginRepositoryAuthProvider.getCustomHeadersSafe(url: String): Map<String, String> =
      withLogging(emptyMap()) { getAuthHeaders(url) }

    private inline fun <T> withLogging(default: T, f: () -> T): T = try {
      f()
    } catch (e: Exception) {
      logger.error(e)
      default
    }

  }
}