// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.ide.plugins.newui.getPluginsViewCustomizer
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal data class UnifiedPluginInternalGroup(
  val title: @Nls String,
  val models: List<PluginUiModel>,
)

internal data class UnifiedPluginInternalSourceState(
  val section: PluginSectionState?,
  val listModelData: PluginListModelData,
  val facetsLoading: Boolean,
  val descriptorRequestSettled: Boolean,
)

internal fun loadUnifiedPluginInternalGroup(): UnifiedPluginInternalGroup? {
  val descriptor = getPluginsViewCustomizer().getInternalPluginsGroupDescriptor() ?: return null
  return UnifiedPluginInternalGroup(descriptor.name, descriptor.plugins.map(::PluginUiModelAdapter))
}

/**
 * Produces the optional internal plugin section and its list data.
 *
 * The command processor serializes all content changes and publications.
 * Request jobs and the update collector send commands instead of changing content directly.
 * The processor can run on any thread from [scope].
 * Call [start] and [close] sequentially from the lifecycle owner.
 * Call request methods only after [start] and before [close] begins.
 * [state] can be collected from any coroutine context.
 */
internal class UnifiedPluginInternalSourceCoordinator(
  private val scope: CoroutineScope,
  private val loadGroup: suspend () -> UnifiedPluginInternalGroup?,
  private val dataEnricher: UnifiedPluginRemoteDataEnricher,
  private val updates: Flow<PluginUpdatesEvent>,
  private val loadingTitle: @Nls String,
  private val loadErrorMessage: @Nls String,
  private val loadingDelay: Duration = 100.milliseconds,
) : AutoCloseable {
  private val commands = Channel<Command>(Channel.UNLIMITED)
  private val mutableState = MutableStateFlow(emptyInternalSourceState())
  private var processorJob: Job? = null
  private var updatesJob: Job? = null
  private var loadingIndicatorJob: Job? = null
  private var requestJob: Job? = null
  private var started = false
  private var closed = false

  private var group: UnifiedPluginInternalGroup? = null
  private var groupResolved = false
  private var descriptorRequestSettled = false
  private var snapshot: UnifiedPluginMarketplaceSnapshot? = null
  private var latestUpdates: PluginUpdatesEvent? = null
  private var updateRevision = 0L
  private var contentRevision = 0L
  private var requestToken = 0L

  val state: StateFlow<UnifiedPluginInternalSourceState> = mutableState.asStateFlow()

  fun start() {
    check(!started) { "Unified plugin internal source coordinator is already started" }
    check(!closed) { "Unified plugin internal source coordinator is closed" }
    started = true
    processorJob = scope.launch { processCommands() }
    updatesJob = scope.launch { updates.collect { commands.trySend(Command.UpdatesChanged(it)) } }
    commands.trySend(Command.Refresh)
  }

  fun retry() {
    if (!closed) commands.trySend(Command.Refresh)
  }

  fun refreshEnrichment() {
    if (!closed) commands.trySend(Command.Refresh)
  }

  override fun close() {
    if (closed) return
    closed = true
    requestJob?.cancel()
    loadingIndicatorJob?.cancel()
    updatesJob?.cancel()
    processorJob?.cancel()
    commands.close()
  }

  private suspend fun processCommands() {
    for (command in commands) {
      when (command) {
        is Command.UpdatesChanged -> {
          latestUpdates = command.updates
          updateRevision++
          if (group != null) startEnrichment()
        }
        Command.Refresh -> refresh()
        is Command.GroupLoadingDelayElapsed -> acceptGroupLoadingDelayElapsed(command)
        is Command.GroupLoaded -> acceptGroupLoaded(command)
        is Command.Loaded -> acceptLoaded(command)
        is Command.Failed -> acceptFailed(command)
      }
    }
  }

  private fun refresh() {
    if (groupResolved) {
      if (group != null) startEnrichment()
    }
    else {
      startGroupLoad()
    }
  }

  private fun startGroupLoad() {
    requestJob?.cancel()
    loadingIndicatorJob?.cancel()
    descriptorRequestSettled = false
    mutableState.value = mutableState.value.copy(descriptorRequestSettled = false)
    val token = ++requestToken
    loadingIndicatorJob = scope.launch {
      delay(loadingDelay)
      commands.trySend(Command.GroupLoadingDelayElapsed(token))
    }
    requestJob = scope.launch {
      try {
        commands.trySend(Command.GroupLoaded(token, loadGroup()))
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (t: Throwable) {
        commands.trySend(Command.Failed(token, t))
      }
    }
  }

  private fun acceptGroupLoaded(command: Command.GroupLoaded) {
    if (command.token != requestToken) return
    requestJob = null
    loadingIndicatorJob?.cancel()
    loadingIndicatorJob = null
    group = command.group
    groupResolved = true
    descriptorRequestSettled = true
    if (command.group == null) {
      mutableState.value = emptyInternalSourceState(descriptorRequestSettled = true)
    }
    else {
      startEnrichment()
    }
  }

  private fun acceptGroupLoadingDelayElapsed(command: Command.GroupLoadingDelayElapsed) {
    if (command.token != requestToken || descriptorRequestSettled) return
    loadingIndicatorJob = null
    mutableState.value = UnifiedPluginInternalSourceState(
      section = PluginSectionState(
        id = PluginSectionId.Internal,
        title = loadingTitle,
        status = PluginSectionStatus.Loading(showingStaleContent = false),
      ),
      listModelData = PluginListModelData.EMPTY,
      facetsLoading = true,
      descriptorRequestSettled = false,
    )
  }

  private fun startEnrichment() {
    requestJob?.cancel()
    val currentGroup = group
    if (currentGroup == null) {
      publish(PluginSectionStatus.Ready)
      return
    }
    val token = ++requestToken
    val updatesAtRequest = updateRevision
    val currentUpdates = latestUpdates
    val revision = ++contentRevision
    publish(PluginSectionStatus.Loading(showingStaleContent = snapshot != null))
    requestJob = scope.launch {
      try {
        val enriched = dataEnricher.enrich(currentGroup.models, currentUpdates, revision)
        commands.trySend(Command.Loaded(token, enriched, updatesAtRequest))
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
    requestJob = null
    snapshot = command.snapshot
    if (command.updateRevision != updateRevision) {
      startEnrichment()
      return
    }
    publish(PluginSectionStatus.Ready)
  }

  private fun acceptFailed(command: Command.Failed) {
    if (command.token != requestToken) return
    requestJob = null
    loadingIndicatorJob?.cancel()
    loadingIndicatorJob = null
    descriptorRequestSettled = true
    LOG.warn("Failed to load internal plugins for the unified Plugins page", command.cause)
    val error = PluginSectionError(loadErrorMessage, retryable = true)
    publish(if (snapshot == null) PluginSectionStatus.Failed(error) else PluginSectionStatus.Degraded(error))
  }

  private fun publish(status: PluginSectionStatus) {
    val currentGroup = group
    mutableState.value = UnifiedPluginInternalSourceState(
      section = if (currentGroup != null) {
        PluginSectionState(
          id = PluginSectionId.Internal,
          title = currentGroup.title,
          items = snapshot?.items.orEmpty(),
          status = status,
        )
      }
      else if (!groupResolved && status is PluginSectionStatus.Failed) {
        PluginSectionState(
          id = PluginSectionId.Internal,
          title = loadingTitle,
          status = status,
        )
      }
      else null,
      listModelData = snapshot?.listModelData ?: PluginListModelData.EMPTY,
      facetsLoading = currentGroup != null && status is PluginSectionStatus.Loading,
      descriptorRequestSettled = descriptorRequestSettled,
    )
  }

  private sealed interface Command {
    data class UpdatesChanged(val updates: PluginUpdatesEvent) : Command
    data object Refresh : Command
    data class GroupLoadingDelayElapsed(val token: Long) : Command
    data class GroupLoaded(val token: Long, val group: UnifiedPluginInternalGroup?) : Command
    data class Loaded(
      val token: Long,
      val snapshot: UnifiedPluginMarketplaceSnapshot,
      val updateRevision: Long,
    ) : Command

    data class Failed(val token: Long, val cause: Throwable) : Command
  }

  private companion object {
    val LOG = logger<UnifiedPluginInternalSourceCoordinator>()
  }
}

private fun emptyInternalSourceState(descriptorRequestSettled: Boolean = false): UnifiedPluginInternalSourceState {
  return UnifiedPluginInternalSourceState(
    section = null,
    listModelData = PluginListModelData.EMPTY,
    facetsLoading = false,
    descriptorRequestSettled = descriptorRequestSettled,
  )
}
