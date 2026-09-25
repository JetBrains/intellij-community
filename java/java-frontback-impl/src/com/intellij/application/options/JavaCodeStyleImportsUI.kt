// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.application.options.JavaCodeStyleImportsPanel.InnerClassItem
import com.intellij.psi.codeStyle.JavaImportsLayoutSettings
import com.intellij.ui.AnActionButtonRunnable
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.table.TableView
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class JavaCodeStyleImportsUI(packages: JComponent,
                                      importLayout: JComponent,
                                      private val doNotInsertInnerTable: TableView<InnerClassItem>,
                                      private val preserveModuleImports: JCheckBox,
                                      private val deleteUnusedModuleImports: JCheckBox,
                                      private val fqnInJavadocOption: JComponent) : CodeStyleImportsBaseUI(packages, importLayout) {

  override fun init() {
    super.init()
    cbInsertInnerClassImports.addActionListener {
      doNotInsertInnerTable.isEnabled = cbInsertInnerClassImports.isSelected
    }
  }

  override fun Panel.fillCustomOptions() {
    indent {
      row {
        val decorator = ToolbarDecorator.createDecorator(doNotInsertInnerTable)
          .setVisibleRowCount(2)
          .setAddAction(AnActionButtonRunnable { addInnerClass() })
          .setRemoveAction(AnActionButtonRunnable { removeInnerClass() }).disableUpDownActions()
        cell(decorator.createPanel())
          .align(Align.FILL)
      }.resizableRow()
    }
    row {
      cell(preserveModuleImports).applyToComponent {
        isOpaque = false
      }
    }
    row {
      cell(deleteUnusedModuleImports).applyToComponent {
        isOpaque = false
      }
    }
    row {
      cell(fqnInJavadocOption)
        .applyToComponent {
          putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY)
        }
    }
  }

  override fun reset(settings: JavaImportsLayoutSettings) {
    super.reset(settings)

    doNotInsertInnerTable.isEnabled = cbInsertInnerClassImports.isSelected
  }

  private fun addInnerClass() {
    val newItems = doNotInsertInnerTable.items.toMutableList() + InnerClassItem("")
    doNotInsertInnerTable.listTableModel.items = newItems
    val index = newItems.size - 1
    doNotInsertInnerTable.selectionModel.setSelectionInterval(index, index)
    doNotInsertInnerTable.scrollRectToVisible(doNotInsertInnerTable.getCellRect(index, 0, true))
  }

  private fun removeInnerClass() {
    TableUtil.removeSelectedItems(doNotInsertInnerTable)
  }
}