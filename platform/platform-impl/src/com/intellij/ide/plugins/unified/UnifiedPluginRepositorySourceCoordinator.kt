// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.CustomPluginRepository
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.ide.plugins.newui.latestCustomRepositoryPlugins
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.concurrent.CancellationException

internal data class UnifiedPluginRepositorySourceState(
  val sections: List<PluginSectionState>,
  val listModelData: PluginListModelData,
  val repositoryPlugins: List<PluginUiModel>?,
  val settledRepositoryPlugins: Map<String, List<PluginUiModel>> = emptyMap(),
  val suggestionsRefreshRevision: Long,
  val repositoryContentRevision: Long = 0,
  val catalogLoading: Boolean = false,
  val facetsLoading: Boolean = false,
)

/**
 * Produces custom repository sections and their list data.
 *
 * The command processor serializes repository entries, revisions, and publication.
 * Request jobs and the update collector send commands instead of changing content directly.
 * The processor can run on any thread from [scope].
 * Call [start] and [close] sequentially from the lifecycle owner.
 * Call request methods only after [start] and before [close] begins.
 * [state] can be collected from any coroutine context.
 */
internal class UnifiedPluginRepositorySourceCoordinator(
  private val scope: CoroutineScope,
  private val repositoryCache: UnifiedPluginRepositoryCache,
  private val dataEnricher: UnifiedPluginRemoteDataEnricher,
  private val updates: Flow<PluginUpdatesEvent>,
  private val loadErrorMessage: @Nls String,
) : AutoCloseable {
  private val commands = Channel<Command>(Channel.UNLIMITED)
  private val mutableState = MutableStateFlow(initialRepositorySourceState())
  private val entries = LinkedHashMap<String, RepositoryEntry>()
  private val requestJobs = HashMap<String, Job>()
  private val pendingSuggestionRefreshes = LinkedHashMap<Long, MutableSet<String>>()
  private var processorJob: Job? = null
  private var updatesJob: Job? = null
  private var catalogJob: Job? = null
  private var sharedFactsRequest: Deferred<Result<UnifiedPluginRemoteSharedFacts>>? = null
  private var started = false
  private var closed = false

  private var catalogLoading = true
  private var catalogToken = 0L
  private var requestToken = 0L
  private var suggestionRefreshToken = 0L
  private var suggestionsRefreshRevision = 0L
  private var latestUpdates: PluginUpdatesEvent? = null
  private var updateRevision = 0L
  private var sharedFactsRevision = 0L
  private var contentRevision = 0L
  private var repositoryContentRevision = 0L
  private var aggregatesRevision = -1L
  private var cachedAggregates = RepositoryAggregates.EMPTY
  private var catalogFailure: PluginSectionError? = null

  val state: StateFlow<UnifiedPluginRepositorySourceState> = mutableState.asStateFlow()

  fun start() {
    check(!started) { "Unified plugin repository source coordinator is already started" }
    check(!closed) { "Unified plugin repository source coordinator is closed" }
    started = true
    processorJob = scope.launch { processCommands() }
    updatesJob = scope.launch { updates.collect { commands.trySend(Command.UpdatesChanged(it)) } }
    commands.trySend(Command.LoadCatalog(refresh = false, notifySuggestions = false))
  }

  fun refresh() {
    if (!closed) commands.trySend(Command.LoadCatalog(refresh = true, notifySuggestions = true))
  }

  fun retry(repositoryId: String) {
    if (!closed) commands.trySend(Command.Retry(repositoryId))
  }

  fun refreshEnrichment() {
    if (!closed) commands.trySend(Command.RefreshEnrichment)
  }

  override fun close() {
    if (closed) return
    closed = true
    catalogJob?.cancel()
    sharedFactsRequest?.cancel()
    sharedFactsRequest = null
    requestJobs.values.forEach(Job::cancel)
    requestJobs.clear()
    updatesJob?.cancel()
    processorJob?.cancel()
    commands.close()
  }

  private suspend fun processCommands() {
    for (command in commands) {
      when (command) {
        is Command.LoadCatalog -> startCatalogLoad(command.refresh, command.notifySuggestions)
        is Command.CatalogLoaded -> acceptCatalog(command)
        is Command.CatalogFailed -> acceptCatalogFailure(command)
        is Command.Retry -> retryRepository(command.repositoryId)
        is Command.UpdatesChanged -> acceptUpdates(command.updates)
        Command.RefreshEnrichment -> acceptSharedFactsChanged()
        is Command.RepositoryLoaded -> acceptRepositoryLoaded(command)
        is Command.RepositoryFailed -> acceptRepositoryFailure(command)
      }
    }
  }

  private fun startCatalogLoad(refresh: Boolean, notifySuggestions: Boolean) {
    catalogJob?.cancel()
    invalidateSharedFacts()
    requestJobs.values.forEach(Job::cancel)
    requestJobs.clear()
    catalogFailure = null
    entries.values.forEach { entry ->
      entry.requestKind = null
      entry.status = PluginSectionStatus.Loading(showingStaleContent = entry.snapshot != null)
    }
    catalogLoading = true
    val token = ++catalogToken
    publish()
    catalogJob = scope.launch {
      try {
        val result = repositoryCache.loadCatalog(refresh)
        commands.trySend(Command.CatalogLoaded(token, refresh, notifySuggestions, result))
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.CatalogFailed(token, notifySuggestions, t))
      }
    }
  }

  private fun acceptCatalog(command: Command.CatalogLoaded) {
    if (command.token != catalogToken) return
    catalogJob = null
    catalogLoading = false
    val result = command.result
    catalogFailure = null
    if (result.error != null) {
      LOG.warn("Failed to load the custom plugin repository catalog for the unified Plugins page: ${result.error}")
    }
    if (result.error != null && result.repositories.isEmpty()) {
      if (entries.isEmpty()) catalogFailure = PluginSectionError(loadErrorMessage, retryable = true)
      else failCurrentCatalogEntries()
      completePendingSuggestionRefreshes(command.notifySuggestions)
      publish()
      return
    }

    val previousEntryIds = entries.keys.toList()
    val previousEntries = LinkedHashMap(entries)
    entries.clear()
    for (repository in result.repositories.distinctBy(CustomPluginRepository::id)) {
      val entry = previousEntries.remove(repository.id) ?: RepositoryEntry(repository)
      entry.repository = repository
      entry.requestKind = null
      entry.status = PluginSectionStatus.Loading(showingStaleContent = entry.snapshot != null)
      entries[repository.id] = entry
    }
    previousEntries.keys.forEach(::markRepositorySettled)
    if (entries.keys.toList() != previousEntryIds) markRepositoryContentChanged()

    if (command.notifySuggestions) {
      registerSuggestionRefresh(entries.keys)
    }
    publish()
    entries.keys.toList().forEach { repositoryId ->
      startRepositoryFetch(repositoryId, refresh = command.refresh)
    }
    if (entries.isEmpty()) publish()
  }

  private fun acceptCatalogFailure(command: Command.CatalogFailed) {
    if (command.token != catalogToken) return
    catalogJob = null
    catalogLoading = false
    LOG.warn("Failed to load the custom plugin repository catalog for the unified Plugins page", command.cause)
    if (entries.isEmpty()) catalogFailure = PluginSectionError(loadErrorMessage, retryable = true)
    else failCurrentCatalogEntries()
    completePendingSuggestionRefreshes(command.notifySuggestions)
    publish()
  }

  private fun failCurrentCatalogEntries() {
    val error = PluginSectionError(loadErrorMessage, retryable = true)
    entries.values.forEach { entry ->
      entry.status = if (entry.snapshot == null) PluginSectionStatus.Failed(error) else PluginSectionStatus.Degraded(error)
      entry.requestKind = null
    }
  }

  private fun retryRepository(repositoryId: String) {
    if (repositoryId !in entries) return
    if (requestJobs.isEmpty()) invalidateSharedFacts()
    registerSuggestionRefresh(listOf(repositoryId))
    startRepositoryFetch(repositoryId, refresh = true)
  }

  private fun acceptUpdates(updates: PluginUpdatesEvent) {
    latestUpdates = updates
    updateRevision++
    entries.values.toList().forEach { entry ->
      if (entry.requestKind != RequestKind.Fetch) entry.fetchedModels?.let { startEnrichment(entry.repository.id, it) }
    }
  }

  private fun acceptSharedFactsChanged() {
    invalidateSharedFacts()
    entries.values.toList().forEach { entry ->
      if (entry.requestKind != RequestKind.Fetch) entry.fetchedModels?.let { startEnrichment(entry.repository.id, it) }
    }
  }

  private fun startRepositoryFetch(repositoryId: String, refresh: Boolean) {
    val entry = entries[repositoryId] ?: return
    requestJobs.remove(repositoryId)?.cancel()
    val token = ++requestToken
    val requestCatalogToken = catalogToken
    val updatesAtRequest = updateRevision
    val sharedFactsAtRequest = sharedFactsRevision
    val sharedFacts = sharedFactsRequest()
    val updates = latestUpdates
    val revision = ++contentRevision
    val repository = entry.repository
    entry.requestToken = token
    entry.requestKind = RequestKind.Fetch
    entry.status = PluginSectionStatus.Loading(showingStaleContent = entry.snapshot != null)
    publish()
    requestJobs[repositoryId] = scope.launch {
      try {
        val result = repositoryCache.loadRepository(repository, refresh)
        if (result.error != null && result.plugins.isEmpty()) {
          commands.trySend(Command.RepositoryFailed(requestCatalogToken, repositoryId, token, result.error, null))
          return@launch
        }
        val models = latestCustomRepositoryPlugins(result.plugins)
        val snapshot = dataEnricher.enrich(models, updates, revision, sharedFacts.await().getOrThrow())
        commands.trySend(
          Command.RepositoryLoaded(
            requestCatalogToken,
            repositoryId,
            token,
            models,
            result.error,
            snapshot,
            updatesAtRequest,
            sharedFactsAtRequest,
          )
        )
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.RepositoryFailed(requestCatalogToken, repositoryId, token, t.message, t))
      }
    }
  }

  private fun startEnrichment(repositoryId: String, models: List<PluginUiModel>) {
    val entry = entries[repositoryId] ?: return
    requestJobs.remove(repositoryId)?.cancel()
    val token = ++requestToken
    val requestCatalogToken = catalogToken
    val updatesAtRequest = updateRevision
    val sharedFactsAtRequest = sharedFactsRevision
    val sharedFacts = sharedFactsRequest()
    val updates = latestUpdates
    val revision = ++contentRevision
    val fetchError = entry.fetchError
    entry.requestToken = token
    entry.requestKind = RequestKind.Enrichment
    entry.status = PluginSectionStatus.Loading(showingStaleContent = entry.snapshot != null)
    publish()
    requestJobs[repositoryId] = scope.launch {
      try {
        val snapshot = dataEnricher.enrich(models, updates, revision, sharedFacts.await().getOrThrow())
        commands.trySend(
          Command.RepositoryLoaded(
            requestCatalogToken,
            repositoryId,
            token,
            models,
            fetchError,
            snapshot,
            updatesAtRequest,
            sharedFactsAtRequest,
          )
        )
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.RepositoryFailed(requestCatalogToken, repositoryId, token, t.message, t))
      }
    }
  }

  private fun acceptRepositoryLoaded(command: Command.RepositoryLoaded) {
    val entry = currentEntry(command.catalogToken, command.repositoryId, command.requestToken) ?: return
    requestJobs.remove(command.repositoryId)
    entry.requestKind = null
    entry.fetchedModels = command.models
    entry.fetchError = command.error
    entry.snapshot = command.snapshot
    markRepositoryContentChanged()
    if (command.updateRevision != updateRevision || command.sharedFactsRevision != sharedFactsRevision) {
      startEnrichment(command.repositoryId, command.models)
      return
    }
    entry.status = command.error?.let {
      PluginSectionStatus.Degraded(PluginSectionError(loadErrorMessage, retryable = true))
    } ?: PluginSectionStatus.Ready
    markRepositorySettled(command.repositoryId)
    publish()
  }

  private fun acceptRepositoryFailure(command: Command.RepositoryFailed) {
    val entry = currentEntry(command.catalogToken, command.repositoryId, command.requestToken) ?: return
    requestJobs.remove(command.repositoryId)
    entry.requestKind = null
    entry.fetchError = command.message
    if (command.cause != null) {
      LOG.warn("Failed to load custom plugin repository ${command.repositoryId} for the unified Plugins page", command.cause)
    }
    else {
      LOG.warn("Failed to load custom plugin repository ${command.repositoryId} for the unified Plugins page: ${command.message}")
    }
    val error = PluginSectionError(loadErrorMessage, retryable = true)
    entry.status = if (entry.snapshot == null) PluginSectionStatus.Failed(error) else PluginSectionStatus.Degraded(error)
    markRepositorySettled(command.repositoryId)
    publish()
  }

  private fun currentEntry(catalogToken: Long, repositoryId: String, requestToken: Long): RepositoryEntry? {
    if (catalogToken != this.catalogToken) return null
    return entries[repositoryId]?.takeIf { it.requestToken == requestToken }
  }

  private fun registerSuggestionRefresh(repositoryIds: Collection<String>) {
    if (repositoryIds.isEmpty()) {
      suggestionsRefreshRevision++
      return
    }
    pendingSuggestionRefreshes[++suggestionRefreshToken] = repositoryIds.toMutableSet()
  }

  private fun completePendingSuggestionRefreshes(includeCurrentRefresh: Boolean) {
    suggestionsRefreshRevision += pendingSuggestionRefreshes.size
    pendingSuggestionRefreshes.clear()
    if (includeCurrentRefresh) suggestionsRefreshRevision++
  }

  private fun markRepositorySettled(repositoryId: String) {
    val completed = ArrayList<Long>()
    for ((token, pendingRepositoryIds) in pendingSuggestionRefreshes) {
      pendingRepositoryIds.remove(repositoryId)
      if (pendingRepositoryIds.isEmpty()) completed.add(token)
    }
    completed.forEach { token ->
      pendingSuggestionRefreshes.remove(token)
      suggestionsRefreshRevision++
    }
  }

  private fun invalidateSharedFacts() {
    sharedFactsRevision++
    sharedFactsRequest = null
  }

  private fun sharedFactsRequest(): Deferred<Result<UnifiedPluginRemoteSharedFacts>> {
    sharedFactsRequest?.let { return it }
    return scope.async {
      try {
        Result.success(dataEnricher.loadSharedFacts())
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        Result.failure(t)
      }
    }.also { sharedFactsRequest = it }
  }

  private fun markRepositoryContentChanged() {
    repositoryContentRevision++
  }

  private fun repositoryAggregates(): RepositoryAggregates {
    if (aggregatesRevision == repositoryContentRevision) return cachedAggregates

    val installedModels = LinkedHashMap<PluginId, PluginUiModel>()
    val errors = LinkedHashMap<PluginId, List<HtmlChunk>>()
    val installationStates = LinkedHashMap<PluginId, PluginInstallationState>()
    val settledRepositoryPlugins = LinkedHashMap<String, List<PluginUiModel>>()
    val allRepositoryPlugins = ArrayList<PluginUiModel>()
    for ((repositoryId, entry) in entries) {
      entry.snapshot?.listModelData?.let { data ->
        data.installedModels.forEach(installedModels::putIfAbsent)
        data.errors.forEach(errors::putIfAbsent)
        data.installationStates.forEach(installationStates::putIfAbsent)
      }
      entry.fetchedModels?.let { models ->
        settledRepositoryPlugins[repositoryId] = models
        allRepositoryPlugins.addAll(models)
      }
    }
    cachedAggregates = RepositoryAggregates(
      listModelData = PluginListModelData(installedModels, errors, installationStates),
      settledRepositoryPlugins = settledRepositoryPlugins,
      repositoryPlugins = latestCustomRepositoryPlugins(allRepositoryPlugins),
    )
    aggregatesRevision = repositoryContentRevision
    return cachedAggregates
  }

  private fun publish() {
    val aggregates = repositoryAggregates()
    val repositoryPlugins = if (catalogLoading || entries.values.any { it.status is PluginSectionStatus.Loading }) {
      null
    }
    else {
      aggregates.repositoryPlugins
    }
    mutableState.value = UnifiedPluginRepositorySourceState(
      sections = buildList {
        catalogFailure?.let { error ->
          add(PluginSectionState(PluginSectionId.CustomRepositoryCatalog, status = PluginSectionStatus.Failed(error)))
        }
        entries.values.mapTo(this) { entry ->
          PluginSectionState(
            id = PluginSectionId.CustomRepository(entry.repository.id),
            title = entry.repository.id,
            items = entry.snapshot?.items.orEmpty(),
            status = entry.status,
          )
        }
      },
      listModelData = aggregates.listModelData,
      repositoryPlugins = repositoryPlugins,
      settledRepositoryPlugins = aggregates.settledRepositoryPlugins,
      suggestionsRefreshRevision = suggestionsRefreshRevision,
      repositoryContentRevision = repositoryContentRevision,
      catalogLoading = catalogLoading,
      facetsLoading = catalogLoading || entries.values.any { it.status is PluginSectionStatus.Loading },
    )
  }

  private class RepositoryEntry(
    var repository: CustomPluginRepository,
    var fetchedModels: List<PluginUiModel>? = null,
    var fetchError: String? = null,
    var snapshot: UnifiedPluginMarketplaceSnapshot? = null,
    var status: PluginSectionStatus = PluginSectionStatus.Loading(showingStaleContent = false),
    var requestToken: Long = 0,
    var requestKind: RequestKind? = null,
  )

  private data class RepositoryAggregates(
    val listModelData: PluginListModelData,
    val settledRepositoryPlugins: Map<String, List<PluginUiModel>>,
    val repositoryPlugins: List<PluginUiModel>,
  ) {
    companion object {
      val EMPTY = RepositoryAggregates(PluginListModelData.EMPTY, emptyMap(), emptyList())
    }
  }

  private sealed interface Command {
    data class LoadCatalog(val refresh: Boolean, val notifySuggestions: Boolean) : Command
    data class CatalogLoaded(
      val token: Long,
      val refresh: Boolean,
      val notifySuggestions: Boolean,
      val result: UnifiedPluginRepositoryCatalogResult,
    ) : Command

    data class CatalogFailed(val token: Long, val notifySuggestions: Boolean, val cause: Throwable) : Command
    data class Retry(val repositoryId: String) : Command
    data class UpdatesChanged(val updates: PluginUpdatesEvent) : Command
    data object RefreshEnrichment : Command
    data class RepositoryLoaded(
      val catalogToken: Long,
      val repositoryId: String,
      val requestToken: Long,
      val models: List<PluginUiModel>,
      val error: String?,
      val snapshot: UnifiedPluginMarketplaceSnapshot,
      val updateRevision: Long,
      val sharedFactsRevision: Long,
    ) : Command

    data class RepositoryFailed(
      val catalogToken: Long,
      val repositoryId: String,
      val requestToken: Long,
      val message: String?,
      val cause: Throwable?,
    ) : Command
  }

  private enum class RequestKind {
    Fetch,
    Enrichment,
  }

  private companion object {
    val LOG = logger<UnifiedPluginRepositorySourceCoordinator>()
  }
}

private fun initialRepositorySourceState(): UnifiedPluginRepositorySourceState {
  return UnifiedPluginRepositorySourceState(
    sections = emptyList(),
    listModelData = PluginListModelData.EMPTY,
    repositoryPlugins = null,
    settledRepositoryPlugins = emptyMap(),
    suggestionsRefreshRevision = 0,
    catalogLoading = true,
    facetsLoading = true,
  )
}
