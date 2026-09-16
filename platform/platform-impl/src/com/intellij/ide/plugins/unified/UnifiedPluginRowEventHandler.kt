// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.EventHandler
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.PluginModelAsyncOperationsExecutor
import com.intellij.ide.plugins.newui.getListPluginComponentCustomizer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.ComponentUtil
import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.AbstractButton
import javax.swing.SwingUtilities

internal class UnifiedPluginRowEventHandler(
  private val onSelectionChanged: (List<PluginOccurrenceId>) -> Unit,
) : EventHandler() {
  private val occurrences = IdentityHashMap<ListPluginComponent, PluginOccurrenceId>()
  private val listenerOwners = IdentityHashMap<Component, ListPluginComponent>()
  private val listenerComponentsByRow = IdentityHashMap<ListPluginComponent, MutableSet<Component>>()
  private var orderedRows: List<ListPluginComponent> = emptyList()
  private var hoveredRow: ListPluginComponent? = null
  private var selectionAnchor: ListPluginComponent? = null

  private val mouseListener = object : MouseAdapter() {
    override fun mouseClicked(event: MouseEvent) {
      if (isPluginRowActionControl(event.component)) return
      val row = findRow(event.component) ?: return
      if (isPluginRowContextMenuEvent(event)) {
        if (row.getSelection() != SelectionType.SELECTION) {
          selectExclusive(row, requestFocus = false)
        }
        showPopup(row, event)
        event.consume()
      }
      else if (SwingUtilities.isLeftMouseButton(event)) {
        when {
          event.isShiftDown -> selectRange(row)
          isPluginRowToggleSelectionEvent(event) -> toggleSelection(row)
          else -> selectExclusive(row, requestFocus = true)
        }
      }
    }

    override fun mouseExited(event: MouseEvent) {
      val row = hoveredRow ?: return
      if (row.getSelection() == SelectionType.HOVER) {
        row.setSelection(SelectionType.NONE, false)
      }
      hoveredRow = null
    }

    override fun mouseMoved(event: MouseEvent) {
      val row = findRow(event.component) ?: return
      if (hoveredRow !== row) {
        hoveredRow?.takeIf { it.getSelection() == SelectionType.HOVER }
          ?.setSelection(SelectionType.NONE, false)
        hoveredRow = row
      }
      if (row.getSelection() == SelectionType.NONE) {
        row.setSelection(SelectionType.HOVER, false)
      }
    }
  }

  private val keyListener = object : KeyAdapter() {
    override fun keyPressed(event: KeyEvent) {
      if (isPluginRowActionControl(event.component)) return
      val row = findRow(event.component) ?: return
      val index = orderedRows.indexOf(row)
      if (index < 0) return

      if (event.keyCode == KeyEvent.VK_A && (event.isMetaDown || event.isControlDown)) {
        event.consume()
        selectAllCompatible(row)
        return
      }

      val targetIndex = when (event.keyCode) {
        KeyEvent.VK_UP -> (index - 1).coerceAtLeast(0)
        KeyEvent.VK_DOWN -> (index + 1).coerceAtMost(orderedRows.lastIndex)
        KeyEvent.VK_HOME -> 0
        KeyEvent.VK_END -> orderedRows.lastIndex
        else -> -1
      }
      if (targetIndex >= 0) {
        event.consume()
        val target = orderedRows[targetIndex]
        if (event.isShiftDown) selectRange(target) else selectExclusive(target, requestFocus = true)
      }
      else if (event.keyCode == KeyEvent.VK_ENTER || event.keyCode == KeyEvent.VK_SPACE || event.keyCode == DELETE_CODE) {
        event.consume()
        val selection = selectedRows().ifEmpty { listOf(row) }
        row.handleKeyAction(event, selection)
        try {
          getListPluginComponentCustomizer().processHandleKeyAction(row, event, selection)
        }
        catch (e: Exception) {
          LOG.error("Error while customizing unified plugin row key action", e)
        }
      }
    }
  }

  private val focusListener = object : FocusAdapter() {
    override fun focusGained(event: FocusEvent) {
      if (isPluginRowActionControl(event.component)) return
      val row = findRow(event.component) ?: return
      if (row.getSelection() != SelectionType.SELECTION) {
        selectExclusive(row, requestFocus = false)
      }
    }
  }

  fun register(occurrenceId: PluginOccurrenceId, row: ListPluginComponent) {
    check(occurrences.put(row, occurrenceId) == null) { "Plugin row is already registered" }
    check(listenerComponentsByRow.put(row, Collections.newSetFromMap(IdentityHashMap())) == null) {
      "Plugin row listeners are already registered"
    }
    try {
      row.setListeners(this)
    }
    catch (t: Throwable) {
      unregister(row)
      throw t
    }
  }

  fun unregister(row: ListPluginComponent) {
    occurrences.remove(row)
    if (hoveredRow === row) {
      hoveredRow = null
    }
    if (selectionAnchor === row) {
      selectionAnchor = null
    }
    removeListeners(row)
  }

  fun renderRows(bindings: List<Pair<PluginOccurrenceId, ListPluginComponent>>) {
    orderedRows = bindings.map { it.second }
    if (selectionAnchor !in orderedRows) selectionAnchor = null
  }

  override fun add(component: Component) {
    val row = findRow(component)?.takeIf(occurrences::containsKey) ?: return
    val previousOwner = listenerOwners[component]
    if (previousOwner != null) {
      check(previousOwner === row) { "Plugin row listener component changed ownership" }
      return
    }
    listenerOwners[component] = row
    checkNotNull(listenerComponentsByRow[row]) { "Plugin row listener owner is not registered" }.add(component)
    component.addMouseListener(mouseListener)
    component.addMouseMotionListener(mouseListener)
    component.addKeyListener(keyListener)
    component.addFocusListener(focusListener)
  }

  private fun selectExclusive(row: ListPluginComponent, requestFocus: Boolean) {
    selectionAnchor = row
    updateSelection(listOf(row), row.takeIf { requestFocus })
  }

  private fun toggleSelection(row: ListPluginComponent) {
    val selected = selectedRows()
    val rowMode = rowMode(row) ?: return
    val updated = if (row.getSelection() == SelectionType.SELECTION) {
      selected.filter { it !== row }
    }
    else if (selected.all { rowMode(it) == rowMode }) {
      orderedRows.filter { it === row || it in selected }
    }
    else {
      listOf(row)
    }
    selectionAnchor = row
    updateSelection(updated, row)
  }

  private fun selectRange(row: ListPluginComponent) {
    val rowIndex = orderedRows.indexOf(row)
    if (rowIndex < 0) return
    val mode = rowMode(row) ?: return
    val anchor = selectionAnchor?.takeIf { it in orderedRows && rowMode(it) == mode } ?: row
    val anchorIndex = orderedRows.indexOf(anchor)
    val range = if (anchorIndex <= rowIndex) anchorIndex..rowIndex else rowIndex..anchorIndex
    val rows = range.map(orderedRows::get).filter { rowMode(it) == mode }
    selectionAnchor = anchor
    updateSelection(rows, row)
  }

  private fun selectAllCompatible(row: ListPluginComponent) {
    val mode = rowMode(row) ?: return
    selectionAnchor = row
    updateSelection(orderedRows.filter { rowMode(it) == mode }, row)
  }

  private fun updateSelection(selected: List<ListPluginComponent>, focusRow: ListPluginComponent?) {
    val selectedSet = selected.toHashSet()
    for (row in orderedRows) {
      row.setSelection(if (row in selectedSet) SelectionType.SELECTION else SelectionType.NONE, false)
    }
    focusRow?.takeIf { it in selectedSet }?.setSelection(SelectionType.SELECTION, true)
    onSelectionChanged(orderedRows.mapNotNull { row -> occurrences[row]?.takeIf { row in selectedSet } })
  }

  private fun selectedRows(): List<ListPluginComponent> {
    return orderedRows.filter { it.getSelection() == SelectionType.SELECTION }
  }

  private fun rowMode(row: ListPluginComponent): PluginDetailsMode? {
    return occurrences[row]?.sectionId?.let(::pluginDetailsMode)
  }

  private fun showPopup(row: ListPluginComponent, event: MouseEvent) {
    val selection = selectedRows().ifEmpty { listOf(row) }
    if (row.getCustomizer() != null) {
      PluginModelAsyncOperationsExecutor.loadPopupMenuActions(row, selection) { actions ->
        showPopup(row, selection, actions, event.component, event.x, event.y)
      }
      return
    }

    val group = DefaultActionGroup()
    row.createPopupMenu(group, selection)
    showPopup(row, selection, group.childActionsOrStubs.toList(), event.component, event.x, event.y)
  }

  private fun showPopup(
    row: ListPluginComponent,
    selection: List<ListPluginComponent>,
    actions: List<com.intellij.openapi.actionSystem.AnAction>,
    eventComponent: Component,
    eventX: Int,
    eventY: Int,
  ) {
    val group = DefaultActionGroup(actions)
    try {
      getListPluginComponentCustomizer().processCreatePopupMenu(row, group, selection)
    }
    catch (e: Exception) {
      LOG.error("Error while customizing unified plugin row popup menu", e)
    }
    if (group.childActionsOrStubs.isEmpty() || !row.isShowing) return

    val popupMenu = ActionManager.getInstance().createActionPopupMenu("PluginManagerConfigurable", group)
    popupMenu.setTargetComponent(row)
    popupMenu.component.show(eventComponent, eventX, eventY)
  }

  private fun removeListeners(row: ListPluginComponent) {
    listenerComponentsByRow.remove(row).orEmpty().forEach { component ->
      check(listenerOwners.remove(component) === row) { "Plugin row listener component changed ownership" }
      component.removeMouseListener(mouseListener)
      component.removeMouseMotionListener(mouseListener)
      component.removeKeyListener(keyListener)
      component.removeFocusListener(focusListener)
    }
  }

  private fun findRow(component: Component): ListPluginComponent? {
    return (component as? ListPluginComponent) ?: ComponentUtil.getParentOfType(ListPluginComponent::class.java, component)
  }
}

internal fun isPluginRowContextMenuEvent(event: MouseEvent, isMac: Boolean = SystemInfo.isMac): Boolean {
  return SwingUtilities.isRightMouseButton(event) || isMac && event.isControlDown && SwingUtilities.isLeftMouseButton(event)
}

internal fun isPluginRowToggleSelectionEvent(event: MouseEvent, isMac: Boolean = SystemInfo.isMac): Boolean {
  return if (isMac) event.isMetaDown else event.isControlDown
}

internal fun isPluginRowActionControl(component: Component): Boolean {
  return component is AbstractButton || ComponentUtil.getParentOfType(AbstractButton::class.java, component) != null
}

private val LOG = logger<UnifiedPluginRowEventHandler>()
