// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.WizardSettingsFactory
import com.intellij.ide.wizard.WizardSettingsProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.SystemProperties
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent

abstract class NewModuleStep<S> : ModuleWizardStep(), WizardSettingsFactory<S> {
  val baseSettings = BaseNewProjectSettings(SystemProperties.getUserHome())

  abstract val panel: JComponent

  abstract fun setupProject(project: Project, settings: S, context: WizardContext)

  final override fun updateDataModel() {
    if (panel is DialogPanel) (panel as DialogPanel).apply()
  }

  final override fun getComponent() = panel

  fun LayoutBuilder.nameAndPath() {
    row(UIBundle.message("label.project.wizard.new.project.name")) {
      textField(baseSettings::name)
    }.largeGapAfter()

    row(UIBundle.message("label.project.wizard.new.project.location")) {
      textFieldWithBrowseButton(baseSettings::path, UIBundle.message("dialog.title.project.name"), /*context.project*/null,
                                FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }.largeGapAfter()
  }

  fun LayoutBuilder.gitCheckbox() {
    row {
      checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"), baseSettings::git)
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

    fun findNonExistingFileName(searchDirectory: String, preferredName: String, extension: String): String {
      var idx = 0
      while (true) {
        val fileName = (if (idx > 0) preferredName + idx else preferredName) + extension
        if (!Files.exists(Paths.get(searchDirectory, fileName))) {
          return fileName
        }
        idx++
      }
    }
  }
}

abstract class NewModuleStepWithSettings<S> : NewModuleStep<S>(), WizardSettingsProvider<S> {
  override val settings by lazy(::createSettings)

  fun setupProject(project: Project, context: WizardContext) {
    BaseNewProjectSettings.KEY.set(context, baseSettings)
    settingsKey.set(context, settings)
    setupProject(project, settings, context)
  }
}

class BaseNewProjectSettings(initPath: String) {
  var path: String = initPath
  var name: String = NewModuleStep.findNonExistingFileName(initPath, "untitled", "")
  var git: Boolean = false

  val projectPath: String
    get() = FileUtil.toCanonicalPath(FileUtil.join(path, name))

  companion object {
    val KEY = Key.create<BaseNewProjectSettings>(BaseNewProjectSettings::class.java.name)
  }
}