// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.fileStatus

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.vcs.FileStatus
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener

@ApiStatus.Internal
class FileStatusColorsPanel(fileStatuses: Array<FileStatus>) {
  private val myTopPanel: JPanel? = null
  private var myFileStatusColorsTable: JBTable? = null
  private var myFileStatusColorBox: JBCheckBox? = null
  private var myRestoreButton: JButton? = null
  private var myColorPanel: ColorPanel? = null
  private val myTablePane: JBScrollPane? = null
  private var myColorSettingsPanel: JPanel? = null
  private val myCustomizedLabel: JLabel? = null
  val model: FileStatusColorsTableModel

  init {
    this.model = FileStatusColorsTableModel(fileStatuses, currentScheme)
    myFileStatusColorsTable!!.setModel(
      this.model)
    (myFileStatusColorsTable as FileStatusColorsTable).adjustColumnWidths()
    model.addTableModelListener(myFileStatusColorsTable)
    myFileStatusColorsTable!!.getSelectionModel().addListSelectionListener(object : ListSelectionListener {
      override fun valueChanged(e: ListSelectionEvent?) {
        updateColorPanel(model.getDescriptorAt(myFileStatusColorsTable!!.getSelectedRow()))
      }
    })
    myRestoreButton!!.addActionListener(object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        restoreDefault(myFileStatusColorsTable!!.getSelectedRow())
      }
    })
    myFileStatusColorBox!!.addActionListener(object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        setUseColor(myFileStatusColorsTable!!.getSelectedRow(), myFileStatusColorBox!!.isSelected())
      }
    })
    myColorPanel!!.addActionListener(object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        setColor(myFileStatusColorsTable!!.getSelectedRow(), myColorPanel!!.getSelectedColor()!!)
      }
    })
    adjustTableSize()
    myColorSettingsPanel!!.setVisible(false)
    updateCustomizedLabel()
    model.addTableModelListener(object : TableModelListener {
      override fun tableChanged(e: TableModelEvent?) {
        updateCustomizedLabel()
      }
    })
  }

  private fun updateCustomizedLabel() {
    val isVisible = model.containsCustomSettings()
    myCustomizedLabel!!.setForeground(if (isVisible) JBColor.GRAY else UIUtil.getLabelBackground())
  }

  private fun adjustTableSize() {
    val d = myFileStatusColorsTable!!.getPreferredSize()
    d.setSize(scale(TABLE_SIZE), d.height)
    myTablePane!!.setMinimumSize(Dimension(scale(TABLE_SIZE), 0))
    myTablePane.setPreferredSize(d)
    myTablePane.setMaximumSize(d)
  }

  val component: JPanel
    get() {
      SwingUtilities.updateComponentTreeUI(myTopPanel) // TODO: create Swing components in this method (see javadoc)
      return myTopPanel!!
    }

  private fun createUIComponents() {
    myFileStatusColorsTable = FileStatusColorsTable()
  }

  private fun updateColorPanel(descriptor: FileStatusColorDescriptor?) {
    if (descriptor == null) {
      myColorSettingsPanel!!.setVisible(false)
    }
    else {
      myColorSettingsPanel!!.setVisible(true)
      myFileStatusColorBox!!.setSelected(descriptor.getColor() != null)
      myRestoreButton!!.setEnabled(!descriptor.isDefault())
      myColorPanel!!.setSelectedColor(descriptor.getColor())
    }
  }

  private fun restoreDefault(row: Int) {
    if (row >= 0) {
      model.resetToDefault(row)
      updateColorPanel(model.getDescriptorAt(row))
    }
  }

  private fun setUseColor(row: Int, useColor: Boolean) {
    if (row >= 0) {
      val descriptor = model.getDescriptorAt(row)
      if (descriptor != null) {
        val defaultColor = descriptor.getDefaultColor()
        val c = if (useColor) if (defaultColor != null) defaultColor else UIUtil.getLabelForeground() else null
        this.model.setValueAt(c, row, 1)
        updateColorPanel(descriptor)
      }
    }
  }

  private fun setColor(row: Int, color: Color) {
    if (row >= 0) {
      model.setValueAt(color, row, 1)
      val descriptor = model.getDescriptorAt(row)
      if (descriptor != null) {
        updateColorPanel(descriptor)
      }
    }
  }

  companion object {
    private const val TABLE_SIZE = 250 // Defined by UI spec

    private val currentScheme: EditorColorsScheme
      get() = EditorColorsManager.getInstance().getSchemeForCurrentUITheme()
  }
}
