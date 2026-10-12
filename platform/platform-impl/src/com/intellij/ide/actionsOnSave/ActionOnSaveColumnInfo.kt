// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actionsOnSave

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.builder.toHtmlIcon
import com.intellij.ui.table.TableView
import com.intellij.util.ui.JBUI
import com.intellij.xml.CommonXmlStrings
import javax.swing.JComponent

internal class ActionOnSaveColumnInfo :
  SameRendererAndEditorColumnInfo<ActionOnSaveInfo>(IdeBundle.message("actions.on.save.table.column.name.action")) {

  override fun getCellComponent(table: TableView<*>, info: ActionOnSaveInfo, hovered: Boolean): JComponent {
    val resultPanel = createActionNamePanel(table, info, hovered)
    resultPanel.border = JBUI.Borders.empty(0, LEFT_RIGHT_INSET)

    setupTableCellBackground(resultPanel, hovered)
    return resultPanel
  }

  private fun createActionNamePanel(
    table: TableView<*>,
    info: ActionOnSaveInfo,
    hovered: Boolean,
  ): DialogPanel {
    if (!info.isSaveActionApplicable) {
      return panel {
        indent {
          row {
            label(info.actionOnSaveName)
              .applyCommentAndConfigure(info.comment)
            addLinks(info, hovered)
          }
        }
      }
    }

    return panel {
      row {
        checkBox(info.actionOnSaveName)
          .applyCommentAndConfigure(info.comment)
          .selected(info.isActionOnSaveEnabled)
          .applyToComponent {
            putClientProperty(DarculaCheckBoxUI.KEEP_BORDER_IN_CELL_RENDERER_PANE, true)

            addActionListener {
              info.isActionOnSaveEnabled = isSelected
              val row = table.editingRow
              val column = table.editingColumn
              if (row >= 0 && column >= 0) {
                // Comment under the checkbox may depend on the checkbox state. Need to re-create the cell editor component.
                table.stopEditing()
                table.editCellAt(row, column)
              }
            }
          }
        addLinks(info, hovered)
      }
    }
  }
}

private fun Row.addLinks(info: ActionOnSaveInfo, hovered: Boolean) {
  if (hovered) {
    for (link in info.actionLinks) {
      cell(link)
    }
  }

  for (control in info.dropDownLinks) {
    cell(control)
  }
}

private fun <T : JComponent> Cell<T>.applyCommentAndConfigure(comment: ActionOnSaveComment?): Cell<T> {
  if (comment != null) {
    val commentText = if (comment.isWarning)
      toHtmlIcon(AllIcons.General::Warning) + CommonXmlStrings.NBSP + comment.commentText
    else comment.commentText
    comment(commentText, maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
  }

  resizableColumn()
  component.minimumSize = JBUI.emptySize()

  return this
}
