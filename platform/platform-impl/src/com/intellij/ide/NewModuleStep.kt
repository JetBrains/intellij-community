// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.execution.ui.CommandLinePanel
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JLabel

abstract class NewModuleStep(private val context: WizardContext) : ModuleWizardStep() {
  private val settings = BaseNewProjectSettings(context)

  final override fun getPreferredFocusedComponent() = panel.preferredFocusedComponent

  final override fun getComponent() = panel

  final override fun updateDataModel() {
    panel.apply()

    context.projectName = settings.name
    context.setProjectFileDirectory(settings.projectPath, false)
  }

  private val panel by lazy {
    panel {
      setupUI(this)
    }.also { panel ->
      panel.withBorder(JBUI.Borders.empty(10, 10))

      val labels = UIUtil.uiChildren(panel).filterIsInstance<JLabel>()
      val width = labels.maxOf { it.preferredSize.width }
      CommandLinePanel.setMinimumWidth(labels.first(), width)
    }
  }

  open fun setupUI(builder: LayoutBuilder) = with(builder) {
    row(UIBundle.message("label.project.wizard.new.project.name")) {
      textField(settings.nameProperty)
        .constraints(pushX)
        .focused()
      installNameGenerators(getBuilderId(), settings.nameProperty)
    }.largeGapAfter()
    row(UIBundle.message("label.project.wizard.new.project.location")) {
      textFieldWithBrowseButton(settings.pathProperty, UIBundle.message("dialog.title.project.name"), context.project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }.largeGapAfter()
    row {
      checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"), settings.gitProperty)
    }.largeGapAfter()
  }

  open fun setupProject(project: Project) {}

  protected fun getBuilderId(): String? {
    val projectBuilder = context.projectBuilder
    if (projectBuilder is NewWizardModuleBuilder) {
      return projectBuilder.builderId
    }
    return null
  }

  init {
    BaseNewProjectSettings.KEY.set(context, settings)
  }

  companion object {
    fun RowBuilder.twoColumnRow(column1: InnerCell.() -> Unit, column2: InnerCell.() -> Unit): Row = row {
      cell {
        column1()
      }
      cell {
        column2()
      }
      placeholder().constraints(growX, pushX)
    }
  }
}
