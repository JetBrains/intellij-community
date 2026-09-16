// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.ide.plugins.newui.calculateTags
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal interface UnifiedPluginLocalDataProvider {
  suspend fun loadInventory(): UnifiedPluginInventory

  suspend fun enrich(
    inventory: UnifiedPluginInventory,
    updates: PluginUpdatesEvent?,
    contentRevision: Long,
  ): UnifiedPluginLocalSnapshot
}

internal fun UnifiedPluginLocalDataProvider.withEnrichmentReadiness(
  awaitEnrichmentReady: suspend () -> Unit,
): UnifiedPluginLocalDataProvider {
  val delegate = this
  return object : UnifiedPluginLocalDataProvider {
    override suspend fun loadInventory(): UnifiedPluginInventory = delegate.loadInventory()

    override suspend fun enrich(
      inventory: UnifiedPluginInventory,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginLocalSnapshot {
      awaitEnrichmentReady()
      return delegate.enrich(inventory, updates, contentRevision)
    }
  }
}

internal data class UnifiedPluginLocalSnapshot(
  val installedItems: List<PluginItemState>,
  val bundledItems: List<PluginItemState>,
  val listModelData: PluginListModelData,
)

internal data class PluginListModelData(
  val installedModels: Map<PluginId, PluginUiModel>,
  val errors: Map<PluginId, List<HtmlChunk>>,
  val installationStates: Map<PluginId, PluginInstallationState>,
) {
  companion object {
    val EMPTY: PluginListModelData = PluginListModelData(emptyMap(), emptyMap(), emptyMap())
  }
}

internal class DefaultUnifiedPluginLocalDataProvider(
  private val host: LegacyPluginUiHost,
  private val pluginManager: UiPluginManager = UiPluginManager.getInstance(),
) : UnifiedPluginLocalDataProvider {
  override suspend fun loadInventory(): UnifiedPluginInventory {
    return normalizePluginInventory(pluginManager.loadPluginInventory())
  }

  override suspend fun enrich(
    inventory: UnifiedPluginInventory,
    updates: PluginUpdatesEvent?,
    contentRevision: Long,
  ): UnifiedPluginLocalSnapshot {
    if (inventory.unavailableSides.isNotEmpty()) {
      return buildDegradedLocalSnapshot(inventory, updates, contentRevision)
    }
    return coroutineScope {
      val plugins = inventory.installedPlugins + inventory.bundledPlugins
      val models = plugins.map(UnifiedPluginInventoryItem::model)
      val pluginIds = models.map(PluginUiModel::pluginId)
      val enabledStates = async { host.getPluginEnabledStates(models) }
      val errors = async { MyPluginModel.getErrors(pluginManager.loadErrors(host.sessionId)) }
      val installationStates = async { pluginManager.getInstallationStates() }
      val restrictions = async {
        if (pluginIds.isEmpty()) emptyMap() else pluginManager.getPluginsRequiresUltimateMap(pluginIds)
      }
      val resolvedRestrictions = restrictions.await()
      buildLocalSnapshot(
        inventory = inventory,
        updates = updates,
        contentRevision = contentRevision,
        enabledStates = enabledStates.await(),
        errors = errors.await(),
        installationStates = installationStates.await(),
        restrictions = resolvedRestrictions,
        tags = models.associate { model ->
          model.pluginId to model.calculateTags(resolvedRestrictions[model.pluginId] == true)
        },
      )
    }
  }
}

internal fun buildDegradedLocalSnapshot(
  inventory: UnifiedPluginInventory,
  updates: PluginUpdatesEvent?,
  contentRevision: Long,
): UnifiedPluginLocalSnapshot {
  val models = (inventory.installedPlugins + inventory.bundledPlugins).map(UnifiedPluginInventoryItem::model)
  return buildLocalSnapshot(
    inventory = inventory,
    updates = updates,
    contentRevision = contentRevision,
    enabledStates = models.associate { it.pluginId to it.isEnabled },
    errors = emptyMap(),
    installationStates = emptyMap(),
    restrictions = emptyMap(),
    tags = models.associate { it.pluginId to it.calculateTags() },
  )
}

internal fun buildLocalSnapshot(
  inventory: UnifiedPluginInventory,
  updates: PluginUpdatesEvent?,
  contentRevision: Long,
  enabledStates: Map<PluginId, Boolean>,
  errors: Map<PluginId, List<HtmlChunk>>,
  installationStates: Map<PluginId, PluginInstallationState>,
  restrictions: Map<PluginId, Boolean>,
  tags: Map<PluginId, List<String>> = emptyMap(),
): UnifiedPluginLocalSnapshot {
  val allPlugins = inventory.installedPlugins + inventory.bundledPlugins
  val updatesById = updates?.all.orEmpty().associateBy(PluginUiModel::pluginId)
  val installedModels = allPlugins.associate { plugin -> plugin.model.pluginId to plugin.model }
  val effectiveStates = allPlugins.associate { plugin ->
    val pluginId = plugin.model.pluginId
    pluginId to (installationStates[pluginId] ?: PluginInstallationState(plugin.runtimeOn != null))
  }

  fun item(plugin: UnifiedPluginInventoryItem): PluginItemState {
    val model = plugin.model
    val pluginId = model.pluginId
    val input = PluginRowInput(
      installedPlugin = model,
      installationState = effectiveStates.getValue(pluginId),
      errors = errors[pluginId].orEmpty().toList(),
      updateDescriptor = updatesById[pluginId],
      enabled = checkNotNull(enabledStates[pluginId]) { "Missing enabled state for $pluginId" },
      restrictedByProduct = restrictions[pluginId] == true,
    )
    return PluginItemState(
      pluginId = pluginId,
      name = model.name,
      contentRevision = contentRevision,
      modelHandle = PluginItemModelHandle(model),
      rowInput = input,
      searchCategory = if (plugin.bundledOn == null) model.displayCategory else bundledPluginCategory(model.displayCategory),
      searchVendor = model.vendor,
      searchTags = tags[pluginId].orEmpty().toSet(),
    )
  }

  fun itemsWithErrorsFirst(plugins: List<UnifiedPluginInventoryItem>): List<PluginItemState> {
    val (withErrors, withoutErrors) = plugins.map(::item).partition { it.rowInput?.errors?.isNotEmpty() == true }
    return withErrors + withoutErrors
  }

  return UnifiedPluginLocalSnapshot(
    installedItems = itemsWithErrorsFirst(inventory.installedPlugins),
    bundledItems = itemsWithErrorsFirst(inventory.bundledPlugins),
    listModelData = PluginListModelData(
      installedModels = installedModels,
      errors = errors.filterKeys(installedModels::containsKey),
      installationStates = effectiveStates,
    ),
  )
}
