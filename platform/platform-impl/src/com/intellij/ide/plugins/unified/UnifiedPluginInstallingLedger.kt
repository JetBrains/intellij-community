// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import java.util.UUID

/**
 * Retains installation attempts for one plugin manager session.
 *
 * Use this class from one serialized execution path. The class has no internal synchronization.
 * The local source command processor provides this serialization.
 * Its coroutine can resume on different threads, so no fixed thread is required.
 */
internal class UnifiedPluginInstallingLedger(
  private val sessionId: String,
) {
  private val mutableEntries = LinkedHashMap<PluginId, MutableEntry>()
  private val operationPlugins = HashMap<UUID, PluginId>()
  private var nextRevision = 0L

  val entries: List<InstallingPluginLedgerEntry>
    get() = mutableEntries.values.map(MutableEntry::snapshot)

  fun accept(event: PluginModelEvent): Boolean {
    return when (event) {
      is PluginModelEvent.OperationStarted -> acceptStarted(event)
      is PluginModelEvent.OperationDependenciesScheduled -> acceptDependenciesScheduled(event)
      is PluginModelEvent.OperationFinished -> acceptFinished(event)
      is PluginModelEvent.InventoryInvalidated -> false
    }
  }

  fun section(listModelData: PluginListModelData): PluginSectionState? {
    if (mutableEntries.isEmpty()) return null
    return PluginSectionState(
      id = PluginSectionId.Installing,
      items = mutableEntries.values.map { entry -> entry.item(listModelData) },
    )
  }

  private fun acceptStarted(event: PluginModelEvent.OperationStarted): Boolean {
    if (event.sessionId != sessionId) return false
    if (operationPlugins.containsKey(event.operationId)) return false

    operationPlugins[event.operationId] = event.displayPluginId
    val revision = ++nextRevision
    val entry = mutableEntries.getOrPut(event.displayPluginId) {
      MutableEntry(event.displayPluginId, event.presentationModel, revision)
    }
    entry.presentationModel = event.presentationModel
    entry.contentRevision = revision
    entry.attempts[event.operationId] = InstallingPluginAttempt(
      operationId = event.operationId,
      target = event.target,
      state = InstallingPluginAttemptState.Active,
    )
    return true
  }

  private fun acceptFinished(event: PluginModelEvent.OperationFinished): Boolean {
    if (event.sessionId != sessionId) return false

    val primaryChanged = acceptPrimaryFinished(event)
    val installedPluginsChanged = event.installedPlugins.fold(false) { changed, plugin ->
      if (plugin.pluginId == event.displayPluginId) changed
      else acceptCompletedInstall(event, plugin) || changed
    }
    val remainingAttemptsChanged = mutableEntries.values.fold(false) { changed, entry ->
      val attempt = entry.attempts[event.operationId]
      if (attempt?.state != InstallingPluginAttemptState.Active) {
        changed
      }
      else {
        entry.contentRevision = ++nextRevision
        entry.attempts[event.operationId] = attempt.copy(
          target = event.target,
          state = InstallingPluginAttemptState.Terminal(event.result),
        )
        true
      }
    }
    return primaryChanged || installedPluginsChanged || remainingAttemptsChanged
  }

  private fun acceptDependenciesScheduled(event: PluginModelEvent.OperationDependenciesScheduled): Boolean {
    if (event.sessionId != sessionId) return false

    return event.dependencies.fold(false) { changed, plugin ->
      if (plugin.pluginId == event.displayPluginId) {
        changed
      }
      else {
        val existingEntry = mutableEntries[plugin.pluginId]
        if (existingEntry?.attempts?.containsKey(event.operationId) == true) {
          changed
        }
        else {
          val revision = ++nextRevision
          val entry = existingEntry ?: MutableEntry(plugin.pluginId, plugin, revision).also {
            mutableEntries[plugin.pluginId] = it
          }
          entry.presentationModel = plugin
          entry.contentRevision = revision
          entry.attempts[event.operationId] = InstallingPluginAttempt(
            operationId = event.operationId,
            target = event.target,
            state = InstallingPluginAttemptState.Active,
          )
          true
        }
      }
    }
  }

  private fun acceptPrimaryFinished(event: PluginModelEvent.OperationFinished): Boolean {
    if (operationPlugins[event.operationId] != event.displayPluginId) return false

    val entry = mutableEntries[event.displayPluginId] ?: return false
    val attempt = entry.attempts[event.operationId] ?: return false
    if (attempt.target != event.target) return false
    if (attempt.state is InstallingPluginAttemptState.Terminal) return false

    entry.contentRevision = ++nextRevision
    entry.attempts[event.operationId] = attempt.copy(
      state = InstallingPluginAttemptState.Terminal(event.result),
    )
    return true
  }

  private fun acceptCompletedInstall(event: PluginModelEvent.OperationFinished, plugin: PluginUiModel): Boolean {
    val existingEntry = mutableEntries[plugin.pluginId]
    if (existingEntry?.attempts?.get(event.operationId)?.state is InstallingPluginAttemptState.Terminal) return false

    val revision = ++nextRevision
    val entry = existingEntry ?: MutableEntry(plugin.pluginId, plugin, revision).also {
      mutableEntries[plugin.pluginId] = it
    }
    entry.presentationModel = plugin
    entry.contentRevision = revision
    entry.attempts[event.operationId] = InstallingPluginAttempt(
      operationId = event.operationId,
      target = event.target,
      state = InstallingPluginAttemptState.Terminal(PluginOperationTerminalResult.SUCCEEDED),
    )
    return true
  }

  private class MutableEntry(
    val pluginId: PluginId,
    var presentationModel: PluginUiModel,
    var contentRevision: Long,
    val attempts: LinkedHashMap<UUID, InstallingPluginAttempt> = LinkedHashMap(),
  ) {
    fun snapshot(): InstallingPluginLedgerEntry {
      return InstallingPluginLedgerEntry(
        pluginId = pluginId,
        presentationModel = presentationModel,
        attempts = attempts.values.toList(),
        contentRevision = contentRevision,
      )
    }

    fun item(listModelData: PluginListModelData): PluginItemState {
      val installedModel = listModelData.installedModels[pluginId]
      return PluginItemState(
        pluginId = pluginId,
        name = presentationModel.name,
        contentRevision = contentRevision,
        modelHandle = PluginItemModelHandle(presentationModel),
        rowInput = PluginRowInput(
          installedPlugin = installedModel,
          installationState = listModelData.installationStates[pluginId] ?: PluginInstallationState(installedModel != null),
          errors = listModelData.errors[pluginId].orEmpty(),
          updateDescriptor = null,
          enabled = true,
          restrictedByProduct = false,
          operationInProgress = attempts.values.any { it.state == InstallingPluginAttemptState.Active },
        ),
      )
    }
  }
}

internal data class InstallingPluginLedgerEntry(
  val pluginId: PluginId,
  val presentationModel: PluginUiModel,
  val attempts: List<InstallingPluginAttempt>,
  val contentRevision: Long,
) {
  val activeOperationIds: Set<UUID>
    get() = attempts.filter { it.state == InstallingPluginAttemptState.Active }
      .mapTo(LinkedHashSet(), InstallingPluginAttempt::operationId)

  val latestTerminalResult: PluginOperationTerminalResult?
    get() = attempts.asReversed().firstNotNullOfOrNull { attempt ->
      (attempt.state as? InstallingPluginAttemptState.Terminal)?.result
    }
}

internal data class InstallingPluginAttempt(
  val operationId: UUID,
  val target: PluginSource,
  val state: InstallingPluginAttemptState,
)

internal sealed interface InstallingPluginAttemptState {
  data object Active : InstallingPluginAttemptState

  data class Terminal(val result: PluginOperationTerminalResult) : InstallingPluginAttemptState
}
