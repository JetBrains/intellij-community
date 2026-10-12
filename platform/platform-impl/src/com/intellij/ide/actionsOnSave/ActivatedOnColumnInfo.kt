// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave

import com.intellij.ide.IdeBundle
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

internal class ActivatedOnColumnInfo :
  SameRendererAndEditorColumnInfo<ActionOnSaveInfo>(IdeBundle.message("actions.on.save.table.column.name.activated.on")) {

  // Affects column width. Use the longest label + space for drop-down arrow + insets
  override fun getMaxStringValue(): String = ActionOnSaveInfo.getExplicitSaveText() + "xxxxxx"

  override fun getCellComponent(table: TableView<*>, info: ActionOnSaveInfo, hovered: Boolean): JComponent {
    val panel = panel {
      row {
        cell(info.activatedOnComponent)
      }
    }.apply {
      border = JBUI.Borders.empty(0, LEFT_RIGHT_INSET)
    }

    setupTableCellBackground(panel, hovered)
    return panel
  }
}
