// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.PluginManagerUiEvent
import com.intellij.ide.plugins.PluginManagerUiMetric
import com.intellij.ide.plugins.PluginManagerUiTracker
import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.findSuggestedPlugins
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.concurrent.CancellationException
import kotlin.time.TimeSource

internal interface UnifiedPluginRemoteDataEnricher {
  suspend fun loadSharedFacts(): UnifiedPluginRemoteSharedFacts = UnifiedPluginRemoteSharedFacts.EMPTY

  suspend fun enrich(
    models: List<PluginUiModel>,
    updates: PluginUpdatesEvent?,
    contentRevision: Long,
  ): UnifiedPluginMarketplaceSnapshot

  suspend fun enrich(
    models: List<PluginUiModel>,
    updates: PluginUpdatesEvent?,
    contentRevision: Long,
    sharedFacts: UnifiedPluginRemoteSharedFacts,
  ): UnifiedPluginMarketplaceSnapshot = enrich(models, updates, contentRevision)
}

internal data class UnifiedPluginRemoteSharedFacts(
  val errors: Map<PluginId, List<HtmlChunk>>,
  val installationStates: Map<PluginId, PluginInstallationState>,
) {
  companion object {
    val EMPTY = UnifiedPluginRemoteSharedFacts(emptyMap(), emptyMap())
  }
}

internal interface UnifiedPluginMarketplaceDataProvider : UnifiedPluginRemoteDataEnricher {
  fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult>

  suspend fun searchMarketplace(query: String): UnifiedPluginMarketplaceFetchResult

  suspend fun loadPopularTags(): List<String> = emptyList()
}

internal fun UnifiedPluginMarketplaceDataProvider.withEnrichmentReadiness(
  awaitEnrichmentReady: suspend () -> Unit,
): UnifiedPluginMarketplaceDataProvider {
  val delegate = this
  return object : UnifiedPluginMarketplaceDataProvider {
    override fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult> = delegate.loadSuggested()

    override suspend fun searchMarketplace(query: String): UnifiedPluginMarketplaceFetchResult = delegate.searchMarketplace(query)

    override suspend fun loadPopularTags(): List<String> = delegate.loadPopularTags()

    override suspend fun loadSharedFacts(): UnifiedPluginRemoteSharedFacts {
      awaitEnrichmentReady()
      return delegate.loadSharedFacts()
    }

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginMarketplaceSnapshot {
      awaitEnrichmentReady()
      return delegate.enrich(models, updates, contentRevision)
    }

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
      sharedFacts: UnifiedPluginRemoteSharedFacts,
    ): UnifiedPluginMarketplaceSnapshot {
      awaitEnrichmentReady()
      return delegate.enrich(models, updates, contentRevision, sharedFacts)
    }
  }
}

internal data class UnifiedPluginMarketplaceFetchResult(
  val models: List<PluginUiModel>,
  val error: String? = null,
  val errorOrigin: MarketplaceFetchErrorOrigin = MarketplaceFetchErrorOrigin.Source,
)

internal enum class MarketplaceFetchErrorOrigin {
  Source,
  CustomRepository,
}

internal data class UnifiedPluginMarketplaceSnapshot(
  val items: List<PluginItemState>,
  val listModelData: PluginListModelData,
)

internal class DefaultUnifiedPluginMarketplaceDataProvider(
  private val host: LegacyPluginUiHost,
  private val project: Project?,
  private val repositoryCache: UnifiedPluginRepositoryCache,
  private val pluginManager: UiPluginManager = UiPluginManager.getInstance(),
) : UnifiedPluginMarketplaceDataProvider {
  private val tracker = PluginManagerUiTracker()

  override fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult> = loadMergedSuggestedPlugins(
    loadProjectSuggestions = ::loadProjectSuggestions,
    loadStaffPicks = ::loadStaffPicks,
    onFailure = { sourceName, cause ->
      LOG.warn("Failed to load $sourceName for the merged Suggested section", cause)
    },
  )

  override suspend fun searchMarketplace(query: String): UnifiedPluginMarketplaceFetchResult {
    val request = buildUnifiedMarketplaceSearchRequest(query)
    if (request.repositories.isNotEmpty()) {
      return UnifiedPluginMarketplaceFetchResult(emptyList())
    }

    val requestStart = TimeSource.Monotonic.markNow()
    val result = try {
      pluginManager.executeMarketplaceQuery(request.urlQuery, MARKETPLACE_RESULT_LIMIT, true)
    }
    catch (c: CancellationException) {
      throw c
    }
    catch (t: Throwable) {
      tracker.measure(PluginManagerUiMetric.SEARCH_MARKETPLACE_REQUEST, requestStart)
      tracker.logEvent(PluginManagerUiEvent.SEARCH_MARKETPLACE_ERROR)
      throw t
    }
    tracker.measure(PluginManagerUiMetric.SEARCH_MARKETPLACE_REQUEST, requestStart)
    if (result.error != null) tracker.logEvent(PluginManagerUiEvent.SEARCH_MARKETPLACE_ERROR)
    val models = normalizeMarketplaceModels(result.getPlugins()).toMutableList()
    if (models.isEmpty()) tracker.logEvent(PluginManagerUiEvent.SEARCH_MARKETPLACE_EMPTY)
    MarketplaceLocalRanker.getInstanceIfEnabled()?.rankPlugins(request.parser, models)
    return UnifiedPluginMarketplaceFetchResult(models, result.error)
  }

  override suspend fun loadPopularTags(): List<String> {
    return sortMarketplaceTags(pluginManager.getMarketplaceTagCounts())
  }

  override suspend fun loadSharedFacts(): UnifiedPluginRemoteSharedFacts = coroutineScope {
    val errors = async { MyPluginModel.getErrors(pluginManager.loadErrors(host.sessionId)) }
    val installationStates = async { pluginManager.getInstallationStates() }
    UnifiedPluginRemoteSharedFacts(errors.await(), installationStates.await())
  }

  override suspend fun enrich(
    models: List<PluginUiModel>,
    updates: PluginUpdatesEvent?,
    contentRevision: Long,
  ): UnifiedPluginMarketplaceSnapshot {
    return enrich(models, updates, contentRevision, loadSharedFacts())
  }

  override suspend fun enrich(
    models: List<PluginUiModel>,
    updates: PluginUpdatesEvent?,
    contentRevision: Long,
    sharedFacts: UnifiedPluginRemoteSharedFacts,
  ): UnifiedPluginMarketplaceSnapshot = coroutineScope {
    val normalizedModels = normalizeMarketplaceModels(models)
    val pluginIds = normalizedModels.mapTo(LinkedHashSet(), PluginUiModel::pluginId)
    val installedModels = async { pluginManager.findInstalledPlugins(pluginIds) }
    val restrictions = async {
      if (pluginIds.isEmpty()) emptyMap() else pluginManager.getPluginsRequiresUltimateMap(pluginIds.toList())
    }
    val installed = installedModels.await()
    val enabledStates = async {
      if (installed.isEmpty()) emptyMap() else host.getPluginEnabledStates(installed.values.toList())
    }
    buildMarketplaceSnapshot(
      models = normalizedModels,
      updates = updates,
      contentRevision = contentRevision,
      installedModels = installed,
      enabledStates = enabledStates.await(),
      errors = sharedFacts.errors,
      installationStates = sharedFacts.installationStates,
      restrictions = restrictions.await(),
    )
  }

  private suspend fun loadProjectSuggestions(): UnifiedPluginMarketplaceFetchResult {
    val currentProject = project ?: return UnifiedPluginMarketplaceFetchResult(emptyList())
    val repositories = repositoryCache.loadAllForSuggestions()
    val models = findSuggestedPlugins(currentProject, repositories.pluginsByRepository).onEach { model ->
      if (model.isFromMarketplace) {
        model.installSource = FUSEventSource.PLUGINS_SUGGESTED_GROUP
      }
      FUSEventSource.PLUGINS_SUGGESTED_GROUP.logPluginSuggested(pluginId = model.pluginId)
    }
    return UnifiedPluginMarketplaceFetchResult(
      models,
      repositories.error,
      MarketplaceFetchErrorOrigin.CustomRepository,
    )
  }

  private suspend fun loadStaffPicks(): UnifiedPluginMarketplaceFetchResult {
    val result = pluginManager.executeMarketplaceQuery(STAFF_PICKS_QUERY, STAFF_PICKS_RESULT_LIMIT, false)
    val models = result.getPlugins().onEach { model ->
      model.installSource = FUSEventSource.PLUGINS_STAFF_PICKS_GROUP
      FUSEventSource.PLUGINS_STAFF_PICKS_GROUP.logPluginSuggested(pluginId = model.pluginId)
    }
    return UnifiedPluginMarketplaceFetchResult(models, result.error)
  }

  private companion object {
    const val STAFF_PICKS_QUERY: String = "is_featured_search=true"
    const val STAFF_PICKS_RESULT_LIMIT: Int = 18
    const val MARKETPLACE_RESULT_LIMIT: Int = 10_000
    val LOG = logger<DefaultUnifiedPluginMarketplaceDataProvider>()
  }
}

internal fun sortMarketplaceTags(tagCounts: Map<String, Int>): List<String> {
  return tagCounts.entries.asSequence()
    .filter { it.key.isNotBlank() }
    .sortedWith(
      compareByDescending<Map.Entry<String, Int>> { it.value }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key }
        .thenBy { it.key }
    )
    .map(Map.Entry<String, Int>::key)
    .toList()
}

internal data class UnifiedMarketplaceSearchRequest(
  val parser: SearchQueryParser.Marketplace,
  val repositories: Set<String>,
  val urlQuery: String,
)

internal fun buildUnifiedMarketplaceSearchRequest(query: String): UnifiedMarketplaceSearchRequest {
  val unifiedQuery = UnifiedPluginsQuery.parse(query)
  val parser = SearchQueryParser.Marketplace(unifiedQuery.renderExcluding { part ->
    part is UnifiedPluginQueryPart.Attribute && part.attribute == UnifiedPluginQueryAttribute.Category
  })
  val categoryParameters = unifiedQuery.categories.asSequence()
    .filterNot(parser.tags::contains)
    .map { category -> "tags=${URLUtil.encodeURIComponent(category)}" }
    .toList()
  val urlQuery = buildList {
    parser.urlQuery.takeIf(String::isNotEmpty)?.let(::add)
    addAll(categoryParameters)
  }.joinToString("&")
  return UnifiedMarketplaceSearchRequest(parser, parser.repositories, urlQuery)
}

internal fun loadMergedSuggestedPlugins(
  loadProjectSuggestions: suspend () -> UnifiedPluginMarketplaceFetchResult,
  loadStaffPicks: suspend () -> UnifiedPluginMarketplaceFetchResult,
  onFailure: (sourceName: String, cause: Throwable) -> Unit = { _, _ -> },
): Flow<UnifiedPluginMarketplaceFetchResult> = flow {
  coroutineScope {
    val sourceResults = Channel<SuggestedSourceResult>(capacity = 2)
    launch {
      sourceResults.send(
        SuggestedSourceResult.Project(loadSuggestedSource("project suggestions", loadProjectSuggestions, onFailure))
      )
    }
    launch {
      sourceResults.send(SuggestedSourceResult.StaffPicks(loadSuggestedSource("Staff Picks", loadStaffPicks, onFailure)))
    }

    var projectSuggestions = UnifiedPluginMarketplaceFetchResult(emptyList())
    var staffPicks = UnifiedPluginMarketplaceFetchResult(emptyList())
    repeat(2) {
      when (val result = sourceResults.receive()) {
        is SuggestedSourceResult.Project -> projectSuggestions = result.value
        is SuggestedSourceResult.StaffPicks -> staffPicks = result.value
      }
      emit(combineSuggestedPluginResults(projectSuggestions, staffPicks))
    }
  }
}

private sealed interface SuggestedSourceResult {
  val value: UnifiedPluginMarketplaceFetchResult

  data class Project(override val value: UnifiedPluginMarketplaceFetchResult) : SuggestedSourceResult
  data class StaffPicks(override val value: UnifiedPluginMarketplaceFetchResult) : SuggestedSourceResult
}

private suspend fun loadSuggestedSource(
  sourceName: String,
  load: suspend () -> UnifiedPluginMarketplaceFetchResult,
  onFailure: (sourceName: String, cause: Throwable) -> Unit,
): UnifiedPluginMarketplaceFetchResult {
  return try {
    load()
  }
  catch (c: CancellationException) {
    throw c
  }
  catch (t: Throwable) {
    onFailure(sourceName, t)
    UnifiedPluginMarketplaceFetchResult(emptyList(), t.message ?: t.javaClass.simpleName)
  }
}

internal fun combineSuggestedPluginResults(
  projectSuggestions: UnifiedPluginMarketplaceFetchResult,
  staffPicks: UnifiedPluginMarketplaceFetchResult,
): UnifiedPluginMarketplaceFetchResult {
  val models = LinkedHashMap<PluginId, PluginUiModel>()
  for (model in projectSuggestions.models) {
    models.putIfAbsent(model.pluginId, model)
  }
  for (model in staffPicks.models) {
    models.putIfAbsent(model.pluginId, model)
  }
  val errors = sequenceOf(projectSuggestions, staffPicks)
    .filter { it.errorOrigin != MarketplaceFetchErrorOrigin.CustomRepository }
    .mapNotNull(UnifiedPluginMarketplaceFetchResult::error)
    .distinct()
    .toList()
  return UnifiedPluginMarketplaceFetchResult(models.values.toList(), errors.takeIf { it.isNotEmpty() }?.joinToString("; "))
}

internal fun normalizeMarketplaceModels(models: List<PluginUiModel>): List<PluginUiModel> {
  return models.associateByTo(LinkedHashMap(), PluginUiModel::pluginId).values.toList()
}

internal fun buildMarketplaceSnapshot(
  models: List<PluginUiModel>,
  updates: PluginUpdatesEvent?,
  contentRevision: Long,
  installedModels: Map<PluginId, PluginUiModel>,
  enabledStates: Map<PluginId, Boolean>,
  errors: Map<PluginId, List<HtmlChunk>>,
  installationStates: Map<PluginId, PluginInstallationState>,
  restrictions: Map<PluginId, Boolean>,
): UnifiedPluginMarketplaceSnapshot {
  val normalizedModels = normalizeMarketplaceModels(models)
  val updatesById = updates?.all.orEmpty().associateBy(PluginUiModel::pluginId)
  val effectiveStates = normalizedModels.associate { model ->
    model.pluginId to (installationStates[model.pluginId] ?: PluginInstallationState(model.pluginId in installedModels))
  }
  val resultIds = normalizedModels.mapTo(HashSet(), PluginUiModel::pluginId)

  val items = normalizedModels.map { model ->
    val pluginId = model.pluginId
    val installedModel = installedModels[pluginId]
    val input = PluginRowInput(
      installedPlugin = installedModel,
      installationState = effectiveStates.getValue(pluginId),
      errors = errors[pluginId].orEmpty().toList(),
      updateDescriptor = updatesById[pluginId],
      enabled = installedModel?.let {
        checkNotNull(enabledStates[pluginId]) { "Missing enabled state for installed Marketplace plugin $pluginId" }
      } ?: false,
      restrictedByProduct = restrictions[pluginId] == true,
    )
    PluginItemState(
      pluginId = pluginId,
      name = model.name,
      contentRevision = contentRevision,
      modelHandle = PluginItemModelHandle(model),
      rowInput = input,
      searchCategory = model.displayCategory,
      searchVendor = model.vendor,
      searchTags = model.tags.orEmpty().toSet(),
    )
  }

  return UnifiedPluginMarketplaceSnapshot(
    items = items,
    listModelData = PluginListModelData(
      installedModels = installedModels.filterKeys(resultIds::contains),
      errors = errors.filterKeys(resultIds::contains),
      installationStates = effectiveStates,
    ),
  )
}
