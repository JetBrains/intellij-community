// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.CustomPluginRepository
import com.intellij.ide.plugins.newui.CustomPluginRepositoryLoadResult
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.UiPluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.concurrent.CancellationException

internal interface UnifiedPluginRepositoryDataProvider {
  suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult

  suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult
}

internal data class UnifiedPluginRepositoryCatalogResult(
  val repositories: List<CustomPluginRepository>,
  val error: String? = null,
)

internal data class UnifiedPluginRepositoryAggregate(
  val pluginsByRepository: Map<String, List<PluginUiModel>>,
  val error: String? = null,
)

internal class DefaultUnifiedPluginRepositoryDataProvider(
  private val pluginManager: UiPluginManager = UiPluginManager.getInstance(),
) : UnifiedPluginRepositoryDataProvider {
  override suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult {
    return UnifiedPluginRepositoryCatalogResult(pluginManager.getCustomPluginRepositories())
  }

  override suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
    return pluginManager.loadCustomPluginRepository(repository)
  }
}

/**
 * Caches custom repository requests for the supplied scope.
 *
 * A mutex protects request state, so callers can use the load methods concurrently.
 * Provider calls run in [scope] and stop when that scope is cancelled.
 */
internal class UnifiedPluginRepositoryCache(
    private val scope: CoroutineScope,
    private val dataProvider: UnifiedPluginRepositoryDataProvider,
) {
  private val lock = Mutex()
  private var requestToken = 0L
  private var catalogRequest: CacheRequest<UnifiedPluginRepositoryCatalogResult>? = null
  private val repositoryRequests = HashMap<String, CacheRequest<CustomPluginRepositoryLoadResult>>()

  suspend fun loadCatalog(refresh: Boolean = false): UnifiedPluginRepositoryCatalogResult {
    val request = lock.withLock {
      val current = catalogRequest
      if (!refresh && current != null) return@withLock current
      val token = ++requestToken
      CacheRequest(
        token,
        scope.async(start = CoroutineStart.LAZY) {
          val result = loadCatalogSafely()
          lock.withLock {
            if (catalogRequest?.token == token && result.error != null && current != null) {
              catalogRequest = current
            }
          }
          result
        },
      ).also { catalogRequest = it }
    }
    request.value.start()
    return request.value.await()
  }

  suspend fun loadRepository(
    repository: CustomPluginRepository,
    refresh: Boolean = false,
  ): CustomPluginRepositoryLoadResult {
    val request = lock.withLock {
      val current = repositoryRequests[repository.id]
      if (!refresh && current != null) return@withLock current
      val token = ++requestToken
      CacheRequest(
        token,
        scope.async(start = CoroutineStart.LAZY) {
          val result = loadRepositorySafely(repository)
          lock.withLock {
            if (repositoryRequests[repository.id]?.token == token && result.error != null && result.plugins.isEmpty() && current != null) {
              repositoryRequests[repository.id] = current
            }
          }
          result
        },
      ).also { repositoryRequests[repository.id] = it }
    }
    request.value.start()
    return request.value.await()
  }

  suspend fun loadAllForSuggestions(): UnifiedPluginRepositoryAggregate = coroutineScope {
    val catalog = loadCatalog()
    val loads = catalog.repositories.map { repository ->
      repository to async { loadRepository(repository) }
    }
    val pluginsByRepository = LinkedHashMap<String, List<PluginUiModel>>()
    val errors = ArrayList<String>()
    catalog.error?.let(errors::add)
    for ((repository, load) in loads) {
      val result = load.await()
      pluginsByRepository[repository.id] = result.plugins
      result.error?.let(errors::add)
    }
    UnifiedPluginRepositoryAggregate(
      pluginsByRepository = pluginsByRepository,
      error = errors.distinct().takeIf { it.isNotEmpty() }?.joinToString("; "),
    )
  }

  private suspend fun loadCatalogSafely(): UnifiedPluginRepositoryCatalogResult {
    return try {
      dataProvider.loadCatalog()
    }
    catch (c: CancellationException) {
      throw c
    }
    catch (t: Throwable) {
      UnifiedPluginRepositoryCatalogResult(emptyList(), t.message ?: t.javaClass.simpleName)
    }
  }

  private suspend fun loadRepositorySafely(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
    return try {
      dataProvider.loadRepository(repository)
    }
    catch (c: CancellationException) {
      throw c
    }
    catch (t: Throwable) {
      CustomPluginRepositoryLoadResult(emptyList(), t.message ?: t.javaClass.simpleName)
    }
  }

  private data class CacheRequest<T>(
    val token: Long,
    val value: Deferred<T>,
  )
}
