// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginPreparedUpdateState
import com.intellij.ide.plugins.newui.PluginProgressState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginStatus
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.isRestartRequired
import com.intellij.openapi.extensions.PluginId
import java.util.UUID

// @spec platform/platform-impl/spec/plugin-manager/plugin-operations.spec.md
/**
 * Retains installation attempts and active uninstalls for one plugin manager session.
 *
 * Use this class from one serialized execution path. The class has no internal synchronization.
 * The local source command processor provides this serialization.
 * Its coroutine can resume on different threads, so no fixed thread is required.
 */
internal class UnifiedPluginInstallingLedger(
  private val sessionId: String,
  private val installHistory: UnifiedPluginInstallHistory = UnifiedPluginInstallHistory(),
) {
  private val mutableEntries = LinkedHashMap<PluginId, MutableEntry>()
  private val operations = LinkedHashMap<UUID, Operation>()
  private val uninstalls = LinkedHashMap<UUID, PluginUiModel>()
  private var nextRevision = 0L

  val entries: List<InstallingPluginLedgerEntry>
    get() = mutableEntries.values.map(MutableEntry::snapshot)

  val uninstallingPlugins: List<PluginUiModel>
    get() = uninstalls.values.distinctBy(PluginUiModel::pluginId)

  fun accept(event: PluginModelEvent, installedPluginIds: Set<PluginId> = emptySet()): Boolean {
    return when (event) {
      is PluginModelEvent.OperationStarted -> acceptStarted(event, installedPluginIds)
      is PluginModelEvent.OperationDependenciesScheduled -> acceptDependenciesScheduled(event, installedPluginIds)
      is PluginModelEvent.OperationFinished -> acceptFinished(event, installedPluginIds)
      is PluginModelEvent.InventoryInvalidated -> false
    }
  }

  fun applyPreparedInstalls() {
    operations.values.filter { it.phase == InstallPhase.Prepared }.forEach { it.phase = InstallPhase.Applying }
  }

  fun resetPreparedInstalls() {
    operations.values.filter { it.phase != InstallPhase.Active }.forEach { it.phase = InstallPhase.Settled }
  }

  fun settleAppliedInstalls() {
    operations.values.filter { it.phase == InstallPhase.Applying }.forEach { it.phase = InstallPhase.Settled }
  }

  fun forgetUninstalledPlugins(pluginIds: Set<PluginId>) {
    operations.values.forEach { it.installIds.removeAll(pluginIds) }
    installHistory.forget(pluginIds)
  }

  fun installedSection(
    inventoryItems: List<PluginItemState>,
    listModelData: PluginListModelData,
    status: PluginSectionStatus,
  ): PluginSectionState {
    val items = inventoryItems.associateByTo(LinkedHashMap(), PluginItemState::pluginId)
    val recentGroups = installHistory.snapshot().toSortedMap(reverseOrder())
    recentGroups.replaceAll { _, group -> group.copy(pluginIds = group.pluginIds.filter(items::containsKey)) }
    recentGroups.values.removeIf { it.pluginIds.isEmpty() }
    val groups = ArrayList<SessionPluginInstallGroup>()
    val displayed = HashSet<PluginId>()
    for ((_, operation) in operations.reversed()) {
      recentGroups.remove(operation.order)
      val installIds = ArrayList<PluginId>()
      for (pluginId in operation.installIds) {
        if (pluginId in displayed) continue
        val entry = mutableEntries.getValue(pluginId)
        val inventoryItem = items[pluginId]
        if (inventoryItem == null && pluginId in listModelData.installedModels) continue
        val model = entry.presentationModel
        if (model.isBundled || model.isBundledUpdate || model.isImplementationDetail) continue

        val item = if (entry.operationInProgress) {
          entry.itemWithInstallProgress(listModelData, inventoryItem)
        }
        else {
          when (operation.phase) {
            InstallPhase.Prepared -> entry.preparedInstallItem(listModelData, inventoryItem, operation.restartRequired)
            InstallPhase.Applying -> inventoryItem ?: entry.preparedInstallItem(listModelData, null, operation.restartRequired)
            InstallPhase.Active, InstallPhase.Settled -> inventoryItem
          }
        } ?: continue
        items[pluginId] = item
        displayed.add(pluginId)
        installIds.add(pluginId)
      }
      if (installIds.isNotEmpty()) {
        val group = SessionPluginInstallGroup(operation.mainPluginId, installIds)
        groups.add(group)
        recentGroups[operation.order] = group
      }
    }
    return PluginSectionState(
      PluginSectionId.Installed,
      items = items.values.toList(),
      status = status,
      sessionInstallGroups = groups,
      recentInstallGroups = recentGroups.values.toList(),
    )
  }

  fun section(listModelData: PluginListModelData): PluginSectionState? {
    if (mutableEntries.isEmpty()) return null
    return PluginSectionState(
      id = PluginSectionId.Installing,
      items = mutableEntries.values.map { entry -> entry.item(listModelData) },
    )
  }

  private fun acceptStarted(event: PluginModelEvent.OperationStarted, installedPluginIds: Set<PluginId>): Boolean {
    if (event.sessionId != sessionId) return false
    if (event.kind == PluginOperationKind.UNINSTALL) return uninstalls.putIfAbsent(event.operationId, event.presentationModel) == null
    if (operations.containsKey(event.operationId)) return false

    val operation = Operation(event.displayPluginId, installHistory.nextOperationOrder()).apply {
      if (event.kind == PluginOperationKind.INSTALL && event.displayPluginId !in installedPluginIds) {
        installIds.add(event.displayPluginId)
      }
    }
    operations[event.operationId] = operation
    rememberInstallGroup(operation)
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

  private fun acceptFinished(event: PluginModelEvent.OperationFinished, installedPluginIds: Set<PluginId>): Boolean {
    if (event.sessionId != sessionId) return false
    if (event.kind == PluginOperationKind.UNINSTALL) return uninstalls.remove(event.operationId) != null

    val primaryChanged = acceptPrimaryFinished(event)
    if (primaryChanged) {
      val operation = operations.getValue(event.operationId)
      val installedDependencies = event.installedPlugins.map(PluginUiModel::pluginId)
      if (event.result != PluginOperationTerminalResult.SUCCEEDED) operation.installIds.remove(event.displayPluginId)
      operation.installIds.retainAll(installedDependencies + event.displayPluginId)
      operation.installIds.addAll(installedDependencies.filter { it != event.displayPluginId && it !in installedPluginIds })
      operation.phase = InstallPhase.Prepared
      operation.restartRequired = event.restartRequired
      rememberInstallGroup(operation)
    }
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

  private fun acceptDependenciesScheduled(
    event: PluginModelEvent.OperationDependenciesScheduled,
    installedPluginIds: Set<PluginId>,
  ): Boolean {
    if (event.sessionId != sessionId || event.kind == PluginOperationKind.UNINSTALL) return false

    val operation = operations[event.operationId]
    val changed = event.dependencies.fold(false) { changed, plugin ->
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
          if (operation?.phase == InstallPhase.Active && plugin.pluginId !in installedPluginIds) {
            operation.installIds.add(plugin.pluginId)
          }
          true
        }
      }
    }
    if (changed && operation != null) rememberInstallGroup(operation)
    return changed
  }

  private fun acceptPrimaryFinished(event: PluginModelEvent.OperationFinished): Boolean {
    if (operations[event.operationId]?.mainPluginId != event.displayPluginId) return false

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

  private fun rememberInstallGroup(operation: Operation) {
    installHistory.remember(operation.order, SessionPluginInstallGroup(operation.mainPluginId, operation.installIds.toList()))
  }

  private class Operation(val mainPluginId: PluginId, val order: Long) {
    val installIds = LinkedHashSet<PluginId>()
    var phase = InstallPhase.Active
    var restartRequired = false
  }

  private enum class InstallPhase {
    Active,
    Prepared,
    /** Apply has cleared the prepared operation state and the inventory reload is pending. */
    Applying,
    Settled,
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
    val operationInProgress: Boolean
      get() = attempts.values.any { it.state == InstallingPluginAttemptState.Active }

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
        updateSourceId = listModelData.updateSources[pluginId],
        modelHandle = PluginItemModelHandle(presentationModel),
        searchCategory = presentationModel.displayCategory,
        searchVendor = presentationModel.vendor,
        searchTags = presentationModel.tags.orEmpty().toSet(),
        rowInput = PluginRowInput(
          installedPlugin = installedModel,
          installationState = listModelData.installationStates[pluginId] ?: PluginInstallationState(installedModel != null),
          errors = listModelData.errors[pluginId].orEmpty(),
          updateDescriptor = null,
          enabled = presentationModel.isEnabled,
          restrictedByProduct = false,
          operationInProgress = operationInProgress,
        ),
      )
    }

    fun itemWithInstallProgress(listModelData: PluginListModelData, inventoryItem: PluginItemState?): PluginItemState {
      val item = inventoryItem ?: item(listModelData)
      val input = checkNotNull(item.rowInput)
      return item.copy(rowInput = input.copy(
        operationInProgress = true,
        detailsProgress = input.detailsProgress ?: PluginProgressState.Indeterminate,
        preparedUpdate = null,
      ))
    }

    fun preparedInstallItem(
      listModelData: PluginListModelData,
      inventoryItem: PluginItemState?,
      restartRequired: Boolean,
    ): PluginItemState? {
      val item = inventoryItem ?: item(listModelData)
      val input = checkNotNull(item.rowInput)
      if (inventoryItem == null && input.errors.isNotEmpty()) return null
      val needsRestart = restartRequired || input.installationState.status.isRestartRequired()
      val status = if (needsRestart) PluginStatus.INSTALLED_AND_REQUIRED_RESTART else PluginStatus.INSTALLED_WITHOUT_RESTART
      return item.copy(rowInput = input.copy(
        installedPlugin = input.installedPlugin ?: presentationModel,
        installationState = inventoryItem?.rowInput?.installationState ?: PluginInstallationState(false, status),
        operationInProgress = false,
        detailsProgress = null,
        preparedUpdate = PluginPreparedUpdateState(needsRestart),
      ))
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
