// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.concurrent.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Produces the Marketplace plugin section and list data.
 *
 * The command processor serializes queries, fetched content, revisions, and publication.
 * Request jobs and the update collector send commands instead of changing content directly.
 * The processor can run on any thread from [scope].
 * Call [start] and [close] sequentially from the lifecycle owner.
 * Call request methods only after [start] and before [close] begins.
 * [state] can be collected from any coroutine context.
 */
internal class UnifiedPluginMarketplaceSourceCoordinator(
  private val scope: CoroutineScope,
  initialQuery: PluginsQueryState,
  private val dataProvider: UnifiedPluginMarketplaceDataProvider,
  private val updates: Flow<PluginUpdatesEvent>,
  private val loadErrorMessage: @Nls String,
  private val searchDebounce: Duration = 300.milliseconds,
) : AutoCloseable {
  private val commands = Channel<Command>(Channel.UNLIMITED)
  private val mutableState = MutableStateFlow(initialMarketplaceSourceState(initialQuery))
  private var processorJob: Job? = null
  private var updatesJob: Job? = null
  private var popularTagsJob: Job? = null
  private var requestJob: Job? = null
  private var requestKind: RequestKind? = null
  private var started = false
  private var closed = false

  private var query = initialQuery
  private var fetchedModels: List<PluginUiModel>? = null
  private var fetchError: String? = null
  private var snapshot: UnifiedPluginMarketplaceSnapshot? = null
  private var suggestedFacetItems: List<PluginItemState> = emptyList()
  private var suggestedFacetsLoading = initialQuery.marketplaceSourceMode() == UnifiedPluginMarketplaceSourceMode.Suggested
  private var popularTags: List<String> = emptyList()
  private var popularTagsLoading = true
  private var latestUpdates: PluginUpdatesEvent? = null
  private var updateRevision = 0L
  private var sharedFactsRevision = 0L
  private var contentRevision = 0L
  private var requestToken = 0L

  val state: StateFlow<UnifiedPluginMarketplaceSourceState> = mutableState.asStateFlow()

  fun start() {
    check(!started) { "Unified plugin Marketplace source coordinator is already started" }
    check(!closed) { "Unified plugin Marketplace source coordinator is closed" }
    started = true
    processorJob = scope.launch { processCommands() }
    updatesJob = scope.launch { updates.collect { commands.trySend(Command.UpdatesChanged(it)) } }
    popularTagsJob = scope.launch {
      try {
        commands.trySend(Command.PopularTagsLoaded(dataProvider.loadPopularTags()))
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.PopularTagsFailed(t))
      }
    }
    commands.trySend(Command.Fetch)
  }

  fun setQuery(query: PluginsQueryState) {
    if (!closed) commands.trySend(Command.QueryChanged(query))
  }

  fun retry() {
    if (!closed) commands.trySend(Command.Fetch)
  }

  fun refreshEnrichment() {
    if (!closed) commands.trySend(Command.RefreshEnrichment)
  }

  override fun close() {
    if (closed) return
    closed = true
    requestJob?.cancel()
    popularTagsJob?.cancel()
    updatesJob?.cancel()
    processorJob?.cancel()
    commands.close()
  }

  private suspend fun processCommands() {
    for (command in commands) {
      when (command) {
        is Command.QueryChanged -> acceptQuery(command.query)
        is Command.UpdatesChanged -> acceptUpdates(command.updates)
        is Command.PopularTagsLoaded -> acceptPopularTagsLoaded(command.tags)
        is Command.PopularTagsFailed -> acceptPopularTagsFailed(command.cause)
        Command.RefreshEnrichment -> {
          sharedFactsRevision++
          if (requestKind != RequestKind.Fetch) fetchedModels?.let(::startEnrichment)
        }
        Command.Fetch -> startFetch(clearSnapshot = false)
        is Command.FetchProgress -> acceptFetchProgress(command)
        is Command.FetchCompleted -> acceptFetchCompleted(command)
        is Command.Loaded -> acceptLoaded(command)
        is Command.Failed -> acceptFailed(command)
      }
    }
  }

  private fun acceptQuery(updatedQuery: PluginsQueryState) {
    require(updatedQuery.revision >= query.revision) { "Marketplace query revision must not move backwards" }
    if (updatedQuery.revision == query.revision) {
      require(updatedQuery.normalizedQuery == query.normalizedQuery) {
        "Normalized Marketplace query must change its revision"
      }
      return
    }
    query = updatedQuery
    startFetch(clearSnapshot = true)
  }

  private fun acceptUpdates(updates: PluginUpdatesEvent) {
    latestUpdates = updates
    updateRevision++
    if (requestKind != RequestKind.Fetch) fetchedModels?.let(::startEnrichment)
  }

  private fun acceptPopularTagsLoaded(tags: List<String>) {
    popularTagsJob = null
    popularTags = tags
    popularTagsLoading = false
    publish(mutableState.value.section.status)
  }

  private fun acceptPopularTagsFailed(cause: Throwable) {
    popularTagsJob = null
    popularTagsLoading = false
    LOG.warn("Failed to load popular Marketplace tags for the unified Plugins page", cause)
    publish(mutableState.value.section.status)
  }

  private fun startFetch(clearSnapshot: Boolean) {
    requestJob?.cancel()
    fetchedModels = null
    fetchError = null
    val requestedQuery = query
    val sourceMode = requestedQuery.marketplaceSourceMode()
    suggestedFacetsLoading = sourceMode == UnifiedPluginMarketplaceSourceMode.Suggested
    if (clearSnapshot || sourceMode == UnifiedPluginMarketplaceSourceMode.Inactive) {
      snapshot = null
    }
    val token = ++requestToken
    if (sourceMode == UnifiedPluginMarketplaceSourceMode.Inactive) {
      requestJob = null
      requestKind = null
      publish(PluginSectionStatus.Ready)
      return
    }
    val updatesAtRequest = updateRevision
    val sharedFactsAtRequest = sharedFactsRevision
    val updates = latestUpdates
    val revision = ++contentRevision
    requestKind = RequestKind.Fetch
    publish(PluginSectionStatus.Loading(showingStaleContent = snapshot != null))
    requestJob = scope.launch {
      try {
        if (sourceMode == UnifiedPluginMarketplaceSourceMode.Suggested) {
          dataProvider.loadSuggested().collect { result ->
            val enriched = if (result.error != null && result.models.isEmpty()) {
              null
            }
            else {
              dataProvider.enrich(result.models, updates, revision)
            }
            commands.trySend(Command.FetchProgress(token, result.models, result.error, enriched))
          }
          commands.trySend(Command.FetchCompleted(token, updatesAtRequest, sharedFactsAtRequest))
          return@launch
        }
        delay(searchDebounce)
        val marketplaceQuery = checkNotNull(requestedQuery.sourceRoute().marketplace.query.takeIf(String::isNotEmpty))
        val result = dataProvider.searchMarketplace(marketplaceQuery)
        if (result.error != null && result.models.isEmpty()) {
          commands.trySend(Command.Failed(token, result.error, null))
          return@launch
        }
        val enriched = dataProvider.enrich(result.models, updates, revision)
        commands.trySend(Command.Loaded(token, result.models, result.error, enriched, updatesAtRequest, sharedFactsAtRequest))
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.Failed(token, t.message, t))
      }
    }
  }

  private fun acceptFetchProgress(command: Command.FetchProgress) {
    if (command.token != requestToken) return
    fetchedModels = command.models
    fetchError = command.error
    command.snapshot?.let {
      snapshot = it
      suggestedFacetItems = it.items
    }
    publish(PluginSectionStatus.Loading(showingStaleContent = snapshot != null))
  }

  private fun acceptFetchCompleted(command: Command.FetchCompleted) {
    if (command.token != requestToken) return
    requestJob = null
    requestKind = null
    suggestedFacetsLoading = false
    val models = fetchedModels.orEmpty()
    if (models.isNotEmpty() &&
        (command.updateRevision != updateRevision || command.sharedFactsRevision != sharedFactsRevision)) {
      startEnrichment(models)
      return
    }
    val error = fetchError
    if (error != null && models.isEmpty()) {
      LOG.warn("Failed to load Suggested plugins for the unified Plugins page: $error")
    }
    val status = when {
      error == null -> PluginSectionStatus.Ready
      models.isNotEmpty() || snapshot != null -> PluginSectionStatus.Degraded(PluginSectionError(loadErrorMessage, retryable = true))
      else -> PluginSectionStatus.Failed(PluginSectionError(loadErrorMessage, retryable = true))
    }
    publish(status)
  }

  private fun startEnrichment(models: List<PluginUiModel>) {
    requestJob?.cancel()
    val token = ++requestToken
    val updatesAtRequest = updateRevision
    val sharedFactsAtRequest = sharedFactsRevision
    val updates = latestUpdates
    val revision = ++contentRevision
    requestKind = RequestKind.Enrichment
    publish(PluginSectionStatus.Loading(showingStaleContent = snapshot != null))
    requestJob = scope.launch {
      try {
        val enriched = dataProvider.enrich(models, updates, revision)
        commands.trySend(Command.Loaded(token, models, fetchError, enriched, updatesAtRequest, sharedFactsAtRequest))
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.Failed(token, t.message, t))
      }
    }
  }

  private fun acceptLoaded(command: Command.Loaded) {
    if (command.token != requestToken) return
    requestJob = null
    requestKind = null
    fetchedModels = command.models
    fetchError = command.error
    snapshot = command.snapshot
    if (query.marketplaceSourceMode() == UnifiedPluginMarketplaceSourceMode.Suggested) {
      suggestedFacetItems = command.snapshot.items
    }
    if (command.updateRevision != updateRevision || command.sharedFactsRevision != sharedFactsRevision) {
      startEnrichment(command.models)
      return
    }
    publish(
      command.error?.let {
        PluginSectionStatus.Degraded(PluginSectionError(loadErrorMessage, retryable = true))
      } ?: PluginSectionStatus.Ready
    )
  }

  private fun acceptFailed(command: Command.Failed) {
    if (command.token != requestToken) return
    requestJob = null
    requestKind = null
    if (query.marketplaceSourceMode() == UnifiedPluginMarketplaceSourceMode.Suggested) {
      suggestedFacetsLoading = false
    }
    if (command.cause != null) {
      LOG.warn("Failed to load Marketplace plugins for the unified Plugins page", command.cause)
    }
    else {
      LOG.warn("Failed to load Marketplace plugins for the unified Plugins page: ${command.message}")
    }
    val error = PluginSectionError(loadErrorMessage, retryable = true)
    publish(if (snapshot == null) PluginSectionStatus.Failed(error) else PluginSectionStatus.Degraded(error))
  }

  private fun publish(status: PluginSectionStatus) {
    val sectionId = marketplaceSectionId(query)
    mutableState.value = UnifiedPluginMarketplaceSourceState(
      queryRevision = query.revision,
      section = PluginSectionState(sectionId, items = snapshot?.items.orEmpty(), status = status),
      listModelData = snapshot?.listModelData ?: PluginListModelData.EMPTY,
      suggestedFacetItems = suggestedFacetItems,
      suggestedFacetsLoading = suggestedFacetsLoading,
      popularTags = popularTags,
      popularTagsLoading = popularTagsLoading,
    )
  }

  private sealed interface Command {
    data class QueryChanged(val query: PluginsQueryState) : Command
    data class UpdatesChanged(val updates: PluginUpdatesEvent) : Command
    data class PopularTagsLoaded(val tags: List<String>) : Command
    data class PopularTagsFailed(val cause: Throwable) : Command
    data object RefreshEnrichment : Command
    data object Fetch : Command
    data class FetchProgress(
        val token: Long,
        val models: List<PluginUiModel>,
        val error: String?,
        val snapshot: UnifiedPluginMarketplaceSnapshot?,
    ) : Command

    data class FetchCompleted(
      val token: Long,
      val updateRevision: Long,
      val sharedFactsRevision: Long,
    ) : Command

    data class Loaded(
        val token: Long,
        val models: List<PluginUiModel>,
        val error: String?,
        val snapshot: UnifiedPluginMarketplaceSnapshot,
        val updateRevision: Long,
        val sharedFactsRevision: Long,
    ) : Command

    data class Failed(val token: Long, val message: String?, val cause: Throwable?) : Command
  }

  private enum class RequestKind {
    Fetch,
    Enrichment,
  }

  private companion object {
    val LOG = logger<UnifiedPluginMarketplaceSourceCoordinator>()
  }
}

private fun initialMarketplaceSourceState(query: PluginsQueryState): UnifiedPluginMarketplaceSourceState {
  return UnifiedPluginMarketplaceSourceState(
    queryRevision = query.revision,
    section = PluginSectionState(
      marketplaceSectionId(query),
      status = initialMarketplaceSectionStatus(query),
    ),
    listModelData = PluginListModelData.EMPTY,
    suggestedFacetsLoading = query.marketplaceSourceMode() == UnifiedPluginMarketplaceSourceMode.Suggested,
    popularTagsLoading = true,
  )
}
