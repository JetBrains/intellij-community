// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.ide.ui.NotPatchedIconRegistry
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.SystemProperties
import javax.swing.JList

private class ShowIconsNotPatchedForExpUi : DumbAwareAction("Show Icons Non Patched For ExpUI") {
  val isEnabled = SystemProperties.getBooleanProperty("ide.experimental.ui.dump.not.patched.icons", false)

  override fun actionPerformed(e: AnActionEvent) {
    val data = NotPatchedIconRegistry.getData().sortedBy { it.originalPath }.toTypedArray()
    val panel = panel {
      row {
        val list = JList(data)
        list.cellRenderer = SimpleListCellRenderer.create { label, model, _ ->
          label.icon = model.icon
          label.text = model.originalPath
        }
        scrollCell(list).align(Align.FILL)
      }.resizableRow()
    }
    dialog("Icons Non Patched For ExpUI", panel, resizable = true).show()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabled
  }
}