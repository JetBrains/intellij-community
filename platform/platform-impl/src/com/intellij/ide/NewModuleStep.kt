// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.WizardSettingsFactory
import com.intellij.ide.wizard.WizardSettingsProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import javax.swing.JComponent

abstract class NewModuleStep<S>(private val context: WizardContext) : ModuleWizardStep(), WizardSettingsFactory<S> {
  val baseSettings = BaseNewProjectSettings(context)

  abstract val panel: JComponent

  abstract fun setupProject(project: Project, settings: S, context: WizardContext)

  final override fun updateDataModel() {
    if (panel is DialogPanel) (panel as DialogPanel).apply()
  }

  final override fun getComponent() = panel

  fun LayoutBuilder.nameAndPath() {
    row(UIBundle.message("label.project.wizard.new.project.name")) {
      textField(baseSettings.nameProperty)
        .constraints(pushX)
        .focused()
      installNameGenerators(getBuilderId(), baseSettings.nameProperty)
    }.largeGapAfter()

    row(UIBundle.message("label.project.wizard.new.project.location")) {
      textFieldWithBrowseButton(baseSettings.pathProperty, UIBundle.message("dialog.title.project.name"), context.project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }.largeGapAfter()
  }

  protected fun getBuilderId(): String? {
    val projectBuilder = context.projectBuilder
    if (projectBuilder is NewWizardModuleBuilder<*>) {
      return projectBuilder.builderId
    }
    return null
  }

  fun LayoutBuilder.gitCheckbox() {
    row {
      checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"), baseSettings.gitProperty)
    }.largeGapAfter()
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

abstract class NewModuleStepWithSettings<S>(context: WizardContext) : NewModuleStep<S>(context), WizardSettingsProvider<S> {
  override val settings by lazy(::createSettings)

  fun setupProject(project: Project, context: WizardContext) {
    BaseNewProjectSettings.KEY.set(context, baseSettings)
    settingsKey.set(context, settings)
    setupProject(project, settings, context)
  }
}
