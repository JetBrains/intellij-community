// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Collects custom auth headers from EPs and optionally provides a HttpRequests.ConnectionTuner with injected headers
 */
@Service(Service.Level.APP)
@ApiStatus.Internal
class PluginRepositoryAuthService {
  val connectionTuner: HttpRequests.ConnectionTuner = HttpRequests.ConnectionTuner { connection ->
    try {
      getAllCustomHeaders(connection.url.toString()).forEach { (k, v) -> connection.addRequestProperty(k, v) }
    }
    catch (e: Exception) {
      thisLogger().warn("Filed to inject headers into request(${connection.url})", e)
    }
  }

  fun getAllCustomHeaders(url: String): Map<String, String> {
    val contributors = PluginRepositoryAuthProvider.EP_NAME.extensionsIfPointIsRegistered.filter {
      runProviderOperation(url, "checking if a provider can handle URL($url)", false) { it.canHandle(url) }
    }

    if (contributors.isEmpty()) return emptyMap()

    if (contributors.size > 1) {
      thisLogger().warn("Multiple contributors tried to inject headers in to url($url): ${contributors.joinToString { it.javaClass.simpleName }}")
    }

    return runProviderOperation(url, "getting custom headers from provider for URL($url)", emptyMap()) {
      contributors.first().getAuthHeaders(url)
    }
  }

  private inline fun <T> runProviderOperation(url: String, operationDescription: String, fallback: T, operation: () -> T): T {
    try {
      return operation()
    }
    catch (e: Throwable) {
      if (e is ControlFlowException || e is CancellationException) {
        throw e
      }
      if (e is Exception || e is LinkageError) {
        thisLogger().warn("Failed while $operationDescription, assuming no custom headers for URL($url)", e)
        return fallback
      }
      throw e
    }
  }
}
