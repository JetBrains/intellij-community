// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

internal interface PluginRowFactory {
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun specification(section: PluginSectionState, item: PluginItemState): PluginRowSpecification<Any>

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun createRow(occurrenceId: PluginOccurrenceId, item: PluginItemState, renderKey: Any): PluginRow

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun rowsRendered(bindings: List<PluginRowBinding<PluginRow>>)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun createReconciler(
    beforeRelease: (PluginOccurrenceId, PluginRow) -> Unit,
  ): PluginRowReconciler<PluginRow, Any> {
    return PluginRowReconciler(::createRow, beforeRelease, PluginRow::close)
  }
}

internal interface PluginRow : AutoCloseable {
  val component: JComponent

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun renderSelection(selected: Boolean)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun close()
}
