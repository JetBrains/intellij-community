// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent
import com.intellij.ide.plugins.newui.PluginPreparedUpdateState
import com.intellij.ide.plugins.newui.PluginProgressState
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.CardLayout
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.JComponent
import javax.swing.JPanel

internal class LegacyPluginDetailsPresenter @RequiresEdt constructor(
  private val host: LegacyPluginUiHost,
  private val searchListener: LinkListener<Any>,
) : PluginDetailsPresenter {
  private val layout = CardLayout()
  private val root = JPanel(layout).apply {
    background = PluginManagerConfigurable.MAIN_BG_COLOR
  }
  private val slots = PluginDetailsMode.entries.associateWithTo(LinkedHashMap(), ::createSlot)
  private var activeMode = PluginDetailsMode.LOCAL
  private var closed = false

  override val component: JComponent = root

  init {
    slots.forEach { (mode, slot) -> root.add(slot.details, mode.name) }
    layout.show(root, activeMode.name)
  }

  @RequiresEdt
  override fun render(mode: PluginDetailsMode, selection: List<PluginDetailsSelection>) {
    check(!closed) { "Plugin details presenter is closed" }
    val selectedRows = selection.map {
      require(pluginDetailsMode(it.occurrenceId.sectionId) == mode) {
        "Details mode $mode does not match ${it.occurrenceId}"
      }
      it.row as? LegacyPluginRow ?: error("Legacy details require a legacy plugin row")
    }

    activeMode = mode
    layout.show(root, mode.name)
    val slot = slots.getValue(mode)
    val readOnlyProgress = selectedRows.singleOrNull()?.detailsProgress
    val preparedUpdate = selectedRows.singleOrNull()?.preparedUpdate
    if (!sameRows(slot.selectedRows, selectedRows) ||
        slot.readOnlyProgress != readOnlyProgress ||
        slot.preparedUpdate != preparedUpdate) {
      slot.selectedRows = selectedRows
      slot.readOnlyProgress = readOnlyProgress
      slot.preparedUpdate = preparedUpdate
      slot.referencedRows.addAll(selectedRows)
      slot.details.showPlugins(
        selectedRows.map(LegacyPluginRow::component),
        readOnlyProgress,
        preparedUpdate,
      )
    }
  }

  @RequiresEdt
  override fun beforeRowRelease(occurrenceId: PluginOccurrenceId, row: PluginRow) {
    if (closed) return
    require(row is LegacyPluginRow) { "Legacy details require a legacy plugin row" }
    require(occurrenceId.pluginId == row.component.getPluginModel().pluginId) {
      "Released row $occurrenceId does not match ${row.component.getPluginModel().pluginId}"
    }

    PluginDetailsMode.entries.forEach { mode ->
      val slot = slots.getValue(mode)
      if (row in slot.selectedRows || row in slot.referencedRows) {
        replaceSlot(mode, slot)
      }
    }
  }

  @RequiresEdt
  override fun close() {
    if (closed) return
    closed = true
    slots.values.forEach { slot -> host.releaseDetails(slot.details) }
    slots.clear()
    root.removeAll()
  }

  private fun createSlot(mode: PluginDetailsMode): DetailsSlot {
    return DetailsSlot(host.createDetails(searchListener, mode == PluginDetailsMode.MARKETPLACE))
  }

  private fun replaceSlot(mode: PluginDetailsMode, slot: DetailsSlot) {
    val previousDetails = slot.details
    host.releaseDetails(previousDetails)
    root.remove(previousDetails)

    val replacement = host.createDetails(searchListener, mode == PluginDetailsMode.MARKETPLACE)
    slot.details = replacement
    slot.selectedRows = emptyList()
    slot.referencedRows.clear()
    root.add(replacement, mode.name)
    if (activeMode == mode) {
      layout.show(root, mode.name)
    }
    root.revalidate()
    root.repaint()
  }

  private class DetailsSlot(
    var details: PluginDetailsPageComponent,
  ) {
    var selectedRows: List<LegacyPluginRow> = emptyList()
    var readOnlyProgress: PluginProgressState? = null
    var preparedUpdate: PluginPreparedUpdateState? = null
    val referencedRows: MutableSet<LegacyPluginRow> = Collections.newSetFromMap(IdentityHashMap())
  }
}

private fun sameRows(first: List<LegacyPluginRow>, second: List<LegacyPluginRow>): Boolean {
  return first.size == second.size && first.indices.all { first[it] === second[it] }
}
