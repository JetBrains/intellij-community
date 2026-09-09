// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.util.concurrency.annotations.RequiresEdt

/** Owns realized plugin rows. Production callers use [com.intellij.ide.plugins.newui.PluginRowRenderKey] as [RenderKey]. */
internal class PluginRowReconciler<Row : Any, RenderKey : Any> @RequiresEdt constructor(
  private val createRow: (PluginOccurrenceId, PluginItemState, RenderKey) -> Row,
  private val beforeRelease: (PluginOccurrenceId, Row) -> Unit,
  private val releaseRow: (Row) -> Unit,
) : AutoCloseable {
  private var entries = LinkedHashMap<PluginOccurrenceId, Entry<Row, RenderKey>>()

  @RequiresEdt
  fun reconcile(specifications: List<PluginRowSpecification<RenderKey>>): List<PluginRowBinding<Row>> {
    validateSpecifications(specifications)

    val nextEntries = LinkedHashMap<PluginOccurrenceId, Entry<Row, RenderKey>>(specifications.size)
    val createdEntries = ArrayList<Entry<Row, RenderKey>>()
    try {
      for ((occurrenceId, item, renderKey) in specifications) {
        val currentEntry = entries[occurrenceId]
        val nextEntry = if (currentEntry?.isCompatibleWith(item, renderKey) == true) {
          currentEntry
        }
        else {
          Entry(checkNotNull(item.modelHandle), renderKey, createRow(occurrenceId, item, renderKey)).also(createdEntries::add)
        }
        nextEntries[occurrenceId] = nextEntry
      }
    }
    catch (t: Throwable) {
      createdEntries.forEach { releaseRow(it.row) }
      throw t
    }

    val removedEntries = entries.filter { (occurrenceId, entry) -> nextEntries[occurrenceId] !== entry }
    entries = nextEntries
    removedEntries.forEach { (occurrenceId, entry) ->
      beforeRelease(occurrenceId, entry.row)
      releaseRow(entry.row)
    }
    return specifications.map { specification ->
      PluginRowBinding(specification.occurrenceId, specification.item, entries.getValue(specification.occurrenceId).row)
    }
  }

  @RequiresEdt
  fun row(occurrenceId: PluginOccurrenceId): Row? = entries[occurrenceId]?.row

  @RequiresEdt
  override fun close() {
    val removedEntries = entries
    entries = LinkedHashMap()
    removedEntries.forEach { (occurrenceId, entry) ->
      beforeRelease(occurrenceId, entry.row)
      releaseRow(entry.row)
    }
  }

  private fun validateSpecifications(specifications: List<PluginRowSpecification<RenderKey>>) {
    val occurrenceIds = HashSet<PluginOccurrenceId>(specifications.size)
    for ((occurrenceId, item) in specifications) {
      require(occurrenceIds.add(occurrenceId)) {
        "Duplicate plugin row occurrence $occurrenceId"
      }
      require(occurrenceId.pluginId == item.pluginId) {
        "Plugin row occurrence $occurrenceId does not match item ${item.pluginId}"
      }
      requireNotNull(item.modelHandle) {
        "Plugin row occurrence $occurrenceId has no model handle"
      }
    }
  }

  private class Entry<Row : Any, RenderKey : Any>(
    private val modelHandle: PluginItemModelHandle,
    private val renderKey: RenderKey,
    val row: Row,
  ) {
    fun isCompatibleWith(item: PluginItemState, nextRenderKey: RenderKey): Boolean {
      return modelHandle == item.modelHandle && renderKey == nextRenderKey
    }
  }
}

internal data class PluginRowSpecification<RenderKey : Any>(
  val occurrenceId: PluginOccurrenceId,
  val item: PluginItemState,
  val renderKey: RenderKey,
)

internal data class PluginRowBinding<Row : Any>(
  val occurrenceId: PluginOccurrenceId,
  val item: PluginItemState,
  val row: Row,
)
