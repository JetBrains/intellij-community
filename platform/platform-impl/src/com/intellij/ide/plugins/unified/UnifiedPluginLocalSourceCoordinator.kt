// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginInventoryChangeReason
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginProgressState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.UUID

internal sealed interface UnifiedPluginManualUpdatePresentation {
  data object Downloading : UnifiedPluginManualUpdatePresentation

  data class Prepared(val restartRequired: Boolean) : UnifiedPluginManualUpdatePresentation
}

internal data class UnifiedPluginManualUpdateState(
  val operationId: UUID,
  val presentation: UnifiedPluginManualUpdatePresentation,
)

internal data class UnifiedPluginLocalSourceState(
  val sections: List<PluginSectionState>,
  val listModelData: PluginListModelData,
  val mayEstablishSelection: Boolean,
  val facetsLoading: Boolean = false,
  val manualUpdates: Map<PluginId, UnifiedPluginManualUpdateState> = emptyMap(),
)

/**
 * Produces the local plugin sections and list data.
 *
 * The command processor serializes content changes and all installing ledger access.
 * Request jobs and event collectors send commands instead of changing content directly.
 * The processor can run on any thread from [scope].
 * Call [start] and [close] sequentially from the lifecycle owner.
 * Call request methods only after [start] and before [close] begins.
 * [state] can be collected from any coroutine context.
 */
internal class UnifiedPluginLocalSourceCoordinator(
  private val scope: CoroutineScope,
  private val dataProvider: UnifiedPluginLocalDataProvider,
  private val updates: Flow<PluginUpdatesEvent>,
  private val hostEvents: Flow<PluginModelEvent>,
  private val sessionId: String,
  private val loadErrorMessage: @Nls String,
  private val hostEventObserver: (PluginModelEvent) -> Unit = {},
) : AutoCloseable {
  private val installingLedger = UnifiedPluginInstallingLedger(sessionId)
  private val commands = Channel<Command>(Channel.UNLIMITED)
  private val mutableState = MutableStateFlow(initialState())
  private var processorJob: Job? = null
  private var updatesJob: Job? = null
  private var eventsJob: Job? = null
  private var requestJob: Job? = null
  private var requestKind: RequestKind? = null
  private var started = false
  private var closed = false

  private var inventory: UnifiedPluginInventory? = null
  private var snapshot: UnifiedPluginLocalSnapshot? = null
  private var latestUpdates: PluginUpdatesEvent? = null
  private var updateRevision = 0L
  private var contentRevision = 0L
  private var requestToken = 0L
  private var inventoryStale = false
  private var updateAllTargets: List<UnifiedPluginUpdateAllTarget> = emptyList()
  private var updateAllRevision = 0L
  private val manualUpdates = HashMap<PluginId, UnifiedPluginManualUpdateState>()

  val state: StateFlow<UnifiedPluginLocalSourceState> = mutableState.asStateFlow()

  fun start() {
    check(!started) { "Unified plugin local source coordinator is already started" }
    check(!closed) { "Unified plugin local source coordinator is closed" }
    started = true
    processorJob = scope.launch { processCommands() }
    updatesJob = scope.launch { updates.collect { commands.trySend(Command.UpdatesChanged(it)) } }
    eventsJob = scope.launch {
      hostEvents.collect { event ->
        commands.trySend(Command.HostEvent(event))
      }
    }
    commands.trySend(Command.Reload)
  }

  fun refresh() {
    if (!closed) commands.trySend(Command.Reload)
  }

  fun setUpdateAllTargets(targets: List<UnifiedPluginUpdateAllTarget>) {
    if (!closed) commands.trySend(Command.UpdateAllTargetsChanged(targets.toList()))
  }

  override fun close() {
    if (closed) return
    closed = true
    requestJob?.cancel()
    updatesJob?.cancel()
    eventsJob?.cancel()
    processorJob?.cancel()
    commands.close()
  }

  private suspend fun processCommands() {
    for (command in commands) {
      when (command) {
        is Command.UpdatesChanged -> {
          latestUpdates = command.updates
          updateRevision++
          if (requestKind != RequestKind.Reload) inventory?.let(::startEnrichment)
        }
        is Command.HostEvent -> acceptHostEvent(command.event)
        is Command.UpdateAllTargetsChanged -> acceptUpdateAllTargets(command.targets)
        Command.Reload -> startReload()
        is Command.Loaded -> acceptLoaded(command)
        is Command.Failed -> acceptFailed(command)
      }
    }
  }

  private fun acceptHostEvent(event: PluginModelEvent) {
    val manualUpdateChanged = acceptManualUpdateEvent(event)
    if (event is PluginModelEvent.InventoryInvalidated) {
      startReload()
    }
    else {
      val installingChanged = installingLedger.accept(event)
      if (manualUpdateChanged || installingChanged) {
        publish(snapshot, currentStatus(), mayEstablishSelection = false)
      }
    }
    hostEventObserver(event)
  }

  private fun acceptManualUpdateEvent(event: PluginModelEvent): Boolean {
    return when (event) {
      is PluginModelEvent.InventoryInvalidated -> {
        when (event.reason) {
          PluginInventoryChangeReason.APPLY -> manualUpdates.entries.removeAll { (_, update) ->
            update.presentation is UnifiedPluginManualUpdatePresentation.Prepared
          }
          PluginInventoryChangeReason.RESET -> {
            if (manualUpdates.isEmpty()) false
            else {
              manualUpdates.clear()
              true
            }
          }
          else -> false
        }
      }
      is PluginModelEvent.OperationStarted -> {
        if (event.sessionId != sessionId || event.kind != PluginOperationKind.UPDATE) return false
        val next = UnifiedPluginManualUpdateState(
          event.operationId,
          UnifiedPluginManualUpdatePresentation.Downloading,
        )
        manualUpdates.put(event.displayPluginId, next) != next
      }
      is PluginModelEvent.OperationFinished -> {
        if (event.sessionId != sessionId || event.kind != PluginOperationKind.UPDATE) return false
        val current = manualUpdates[event.displayPluginId]?.takeIf { it.operationId == event.operationId } ?: return false
        if (event.result == PluginOperationTerminalResult.SUCCEEDED) {
          val next = current.copy(
            presentation = UnifiedPluginManualUpdatePresentation.Prepared(event.restartRequired),
          )
          manualUpdates[event.displayPluginId] = next
          next != current
        }
        else {
          manualUpdates.remove(event.displayPluginId)
          true
        }
      }
      is PluginModelEvent.OperationDependenciesScheduled -> false
    }
  }

  private fun acceptUpdateAllTargets(targets: List<UnifiedPluginUpdateAllTarget>) {
    if (updateAllTargets == targets) return
    updateAllTargets = targets
    updateAllRevision++
    publish(snapshot, currentStatus(), mayEstablishSelection = false)
  }

  private fun startReload() {
    requestJob?.cancel()
    val token = ++requestToken
    val updatesAtRequest = updateRevision
    val updates = latestUpdates
    val revision = ++contentRevision
    requestKind = RequestKind.Reload
    publish(snapshot, PluginSectionStatus.Loading(showingStaleContent = snapshot != null))
    requestJob = scope.launch {
      try {
        val loadedInventory = dataProvider.loadInventory()
        val loadedSnapshot = dataProvider.enrich(loadedInventory, updates, revision)
        commands.trySend(Command.Loaded(token, loadedInventory, loadedSnapshot, updatesAtRequest))
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.Failed(token, t))
      }
    }
  }

  private fun startEnrichment(currentInventory: UnifiedPluginInventory) {
    requestJob?.cancel()
    val token = ++requestToken
    val updatesAtRequest = updateRevision
    val updates = latestUpdates
    val revision = ++contentRevision
    requestKind = RequestKind.Enrichment
    publish(snapshot, PluginSectionStatus.Loading(showingStaleContent = snapshot != null))
    requestJob = scope.launch {
      try {
        val enriched = dataProvider.enrich(currentInventory, updates, revision)
        commands.trySend(Command.Loaded(token, currentInventory, enriched, updatesAtRequest))
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.Failed(token, t))
      }
    }
  }

  private fun acceptLoaded(command: Command.Loaded) {
    if (command.token != requestToken) return
    val completedRequestKind = requestKind
    requestJob = null
    requestKind = null
    inventory = command.inventory
    snapshot = command.snapshot
    if (completedRequestKind == RequestKind.Reload) {
      inventoryStale = false
    }
    if (command.updateRevision != updateRevision) {
      startEnrichment(command.inventory)
      return
    }
    val status = if (inventoryStale || command.inventory.unavailableSides.isNotEmpty()) {
      PluginSectionStatus.Degraded(PluginSectionError(loadErrorMessage, retryable = true))
    }
    else {
      PluginSectionStatus.Ready
    }
    publish(command.snapshot, status)
  }

  private fun acceptFailed(command: Command.Failed) {
    if (command.token != requestToken) return
    val failedRequestKind = requestKind
    requestJob = null
    requestKind = null
    if (failedRequestKind == RequestKind.Reload && snapshot != null) {
      inventoryStale = true
    }
    LOG.warn("Failed to load local plugins for the unified Plugins page", command.cause)
    val error = PluginSectionError(loadErrorMessage, retryable = true)
    publish(
      snapshot,
      if (snapshot == null) PluginSectionStatus.Failed(error) else PluginSectionStatus.Degraded(error),
    )
  }

  private fun publish(
    currentSnapshot: UnifiedPluginLocalSnapshot?,
    status: PluginSectionStatus,
    mayEstablishSelection: Boolean = true,
  ) {
    val listModelData = currentSnapshot?.listModelData ?: PluginListModelData.EMPTY
    mutableState.value = UnifiedPluginLocalSourceState(
      sections = buildList {
        val installingItems = installingItems(listModelData)
        if (installingItems.isNotEmpty()) {
          add(PluginSectionState(PluginSectionId.Installing, items = installingItems))
        }
        add(PluginSectionState(PluginSectionId.Installed, items = currentSnapshot?.installedItems.orEmpty(), status = status))
        add(PluginSectionState(PluginSectionId.Bundled, items = currentSnapshot?.bundledItems.orEmpty(), status = status))
      },
      listModelData = listModelData,
      mayEstablishSelection = mayEstablishSelection,
      facetsLoading = status is PluginSectionStatus.Loading,
      manualUpdates = manualUpdates.toMap(),
    )
  }

  private fun currentStatus(): PluginSectionStatus {
    return mutableState.value.sections.single { it.id == PluginSectionId.Installed }.status
  }

  private fun installingItems(listModelData: PluginListModelData): List<PluginItemState> {
    val items = LinkedHashMap<PluginId, PluginItemState>()
    installingLedger.section(listModelData)?.items.orEmpty().forEach { item -> items[item.pluginId] = item }
    updateAllTargets.forEach { target ->
      val item = updateAllInstallingItem(target, listModelData, updateAllRevision)
      items.putIfAbsent(item.pluginId, item)
    }
    return items.values.toList()
  }

  private sealed interface Command {
    data class UpdatesChanged(val updates: PluginUpdatesEvent) : Command
    data class HostEvent(val event: PluginModelEvent) : Command
    data class UpdateAllTargetsChanged(val targets: List<UnifiedPluginUpdateAllTarget>) : Command
    data object Reload : Command
    data class Loaded(
      val token: Long,
      val inventory: UnifiedPluginInventory,
      val snapshot: UnifiedPluginLocalSnapshot,
      val updateRevision: Long,
    ) : Command

    data class Failed(val token: Long, val cause: Throwable) : Command
  }

  private enum class RequestKind {
    Reload,
    Enrichment,
  }

  private companion object {
    val LOG = logger<UnifiedPluginLocalSourceCoordinator>()
  }
}

internal fun updateAllInstallingItem(
  target: UnifiedPluginUpdateAllTarget,
  listModelData: PluginListModelData,
  contentRevision: Long,
): PluginItemState {
  val model = target.model
  val installedModel = listModelData.installedModels[model.pluginId]
  return PluginItemState(
    pluginId = model.pluginId,
    name = model.name,
    contentRevision = contentRevision,
    modelHandle = PluginItemModelHandle(model),
    rowInput = PluginRowInput(
      installedPlugin = installedModel,
      installationState = listModelData.installationStates[model.pluginId] ?: PluginInstallationState(installedModel != null),
      errors = listModelData.errors[model.pluginId].orEmpty(),
      updateDescriptor = model,
      enabled = installedModel?.isEnabled ?: true,
      restrictedByProduct = false,
      operationInProgress = target.status == UnifiedPluginUpdateAllTargetStatus.Active,
      detailsProgress = PluginProgressState.Indeterminate.takeIf {
        target.status == UnifiedPluginUpdateAllTargetStatus.Active
      },
    ),
  )
}

private fun initialState(): UnifiedPluginLocalSourceState {
  val loading = PluginSectionStatus.Loading(showingStaleContent = false)
  return UnifiedPluginLocalSourceState(
    sections = listOf(
      PluginSectionState(PluginSectionId.Installed, status = loading),
      PluginSectionState(PluginSectionId.Bundled, status = loading),
    ),
    listModelData = PluginListModelData.EMPTY,
    mayEstablishSelection = true,
    facetsLoading = true,
  )
}
