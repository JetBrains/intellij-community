// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

internal interface PluginRowFactory {
  @RequiresEdt
  fun specification(section: PluginSectionState, item: PluginItemState): PluginRowSpecification<Any>

  @RequiresEdt
  fun createRow(occurrenceId: PluginOccurrenceId, item: PluginItemState, renderKey: Any): PluginRow

  @RequiresEdt
  fun rowsRendered(bindings: List<PluginRowBinding<PluginRow>>)

  @RequiresEdt
  fun createReconciler(
    beforeRelease: (PluginOccurrenceId, PluginRow) -> Unit,
  ): PluginRowReconciler<PluginRow, Any> {
    return PluginRowReconciler(::createRow, beforeRelease, PluginRow::close)
  }
}

internal interface PluginRow : AutoCloseable {
  val component: JComponent

  @RequiresEdt
  fun renderSelection(selected: Boolean)

  @RequiresEdt
  override fun close()
}
