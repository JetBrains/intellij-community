// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.fileStatus

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.Configurable.VariableProjectAppLevel
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vcs.FileStatusFactory
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.ui.ColorPanel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ValueComponentPredicate
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JButton
import javax.swing.JCheckBox

@ApiStatus.Internal
class FileStatusColorsConfigurable : BoundSearchableConfigurable(
  displayName = ApplicationBundle.message("title.file.status.colors"),
  helpTopic = "reference.versionControl.highlight",
  _id = "file.status.colors"
), NoScroll, VariableProjectAppLevel {

  override fun createPanel(): DialogPanel {
    val currentUiScheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
    val fileStatuses = FileStatusFactory.getInstance().allFileStatuses

    val tableModel = FileStatusColorsTableModel(fileStatuses, currentUiScheme)
    val table = FileStatusColorsTable(tableModel)

    lateinit var useColorCheckBox: JCheckBox
    lateinit var colorPanel: ColorPanel
    lateinit var resetColorButton: JButton
    val colorSettingsVisible = ValueComponentPredicate(true)

    fun updateColorPanel() {
      val descriptor = tableModel.getDescriptorAt(table.selectedRow)
      if (descriptor == null) {
        colorSettingsVisible.set(false)
      }
      else {
        colorSettingsVisible.set(true)
        useColorCheckBox.setSelected(descriptor.color != null)
        colorPanel.setSelectedColor(descriptor.color)
        resetColorButton.setEnabled(!descriptor.isDefault)
      }
    }
    table.selectionModel.addListSelectionListener { updateColorPanel() }
    tableModel.addTableModelListener { updateColorPanel() }

    val panel = panel {
      row {
        scrollCell(table)
          .align(Align.FILL)
          .onIsModified {
            tableModel.isModified
          }
          .onApply {
            tableModel.apply()
            for (project in ProjectManager.getInstance().getOpenProjects()) {
              FileStatusManager.getInstance(project).fileStatusesChanged()
            }
          }
          .onReset {
            tableModel.reset()
          }

        panel {
          row {
            @Suppress("DialogTitleCapitalization")
            useColorCheckBox = checkBox(ApplicationBundle.message("title.file.status.color"))
              .applyToComponent {
                addActionListener { event ->
                  val useColor = useColorCheckBox.isSelected
                  val descriptor = tableModel.getDescriptorAt(table.selectedRow) ?: return@addActionListener
                  val defaultColor = descriptor.defaultColor
                  val newColor = if (useColor) defaultColor ?: UIUtil.getLabelForeground() else null
                  tableModel.setColorFor(descriptor, newColor)
                }
              }
              .component
            colorPanel = cell(ColorPanel())
              .applyToComponent {
                addActionListener { event ->
                  val descriptor = tableModel.getDescriptorAt(table.selectedRow) ?: return@addActionListener
                  tableModel.setColorFor(descriptor, colorPanel.selectedColor)
                }
              }
              .component
          }
          row {
            resetColorButton = button(ApplicationBundle.message("title.file.status.color.restore.default")) {
              val descriptor = tableModel.getDescriptorAt(table.selectedRow) ?: return@button
              tableModel.resetToDefault(descriptor)
            }
              .component
          }
        }
          .visibleIf(colorSettingsVisible)
          .resizableColumn()
          .align(AlignY.TOP)
      }.resizableRow()
      row {
        @Suppress("DialogTitleCapitalization")
        comment(LangBundle.message("label.file.status.customized"))
      }
    }

    updateColorPanel()
    return panel
  }

  override fun isProjectLevel(): Boolean {
    return false
  }
}
