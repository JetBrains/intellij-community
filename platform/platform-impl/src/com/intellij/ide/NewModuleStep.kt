// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.SystemProperties
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JComponent

abstract class NewModuleStep<T> : ModuleWizardStep() {
  abstract val panel: JComponent
  abstract var settings: T

  var baseSettings = BaseNewProjectSettings(SystemProperties.getUserHome())

  final override fun updateDataModel() {
    if (panel is DialogPanel) (panel as DialogPanel).apply()
  }

  final override fun getComponent() = panel

  fun LayoutBuilder.nameAndPath() {
    twoColumnRow(
      { label(UIBundle.message("label.project.wizard.new.project.name")) },
      { textField(baseSettings::name) }
    )

    twoColumnRow(
      { label(UIBundle.message("label.project.wizard.new.project.location")) },
      {
        textFieldWithBrowseButton(baseSettings::path, UIBundle.message("dialog.title.project.name"), /*context.project*/null,
                                  FileChooserDescriptorFactory.createSingleFolderDescriptor())
      }
    )
  }

  fun LayoutBuilder.gitCheckbox() {
    row {
      checkBox(UIBundle.message("label.project.wizard.new.project.git.checkbox"), baseSettings::git)
    }
  }

  fun LayoutBuilder.advancedModuleSettings() {
    hideableRow(UIBundle.message("label.project.wizard.new.project.advanced.settings")) {
      row {
        cell { label(UIBundle.message("label.project.wizard.new.project.module.name")) }
        cell {
          textField(baseSettings::moduleName)
        }
      }

      row{
        cell { label(UIBundle.message("label.project.wizard.new.project.content.root")) }
        cell {
          textFieldWithBrowseButton(baseSettings::contentRoot, UIBundle.message("label.project.wizard.new.project.content.root"), null,
                                    FileChooserDescriptorFactory.createSingleFolderDescriptor())
        }
      }

      row{
        cell { label(UIBundle.message("label.project.wizard.new.project.module.file.location")) }
        cell {
          textFieldWithBrowseButton(baseSettings::moduleFileLocation,
                                    UIBundle.message("label.project.wizard.new.project.module.file.location"), null,
                                    FileChooserDescriptorFactory.createSingleFolderDescriptor())
        }
      }
    }
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

class BaseNewProjectSettings(initPath: String) {
  var path: String = initPath
  var name: String = NewModuleStep.findNonExistingFileName(initPath, "untitled", "")
  var git: Boolean = false

  var moduleName: String = ""
  var contentRoot: String = ""
  var moduleFileLocation: String = ""
}