/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.settings

import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.layout.*
import javax.swing.AbstractButton
import javax.swing.JComponent

class ExternalDiffSettingsPanel {
  companion object {
    private const val DESCRIPTION_TEXT = "<html>" +
                                         "Different tools have different parameters. It's important to specify all necessary parameters in proper order<br>" +
                                         "<b>%1</b> - left (Local changes)<br>" +
                                         "<b>%2</b> - right (Server content)<br>" +
                                         "<b>%3</b> - base (Current version without local changes)<br>" +
                                         "<b>%4</b> - output (Merge result)" +
                                         "</html>"
  }

  private val panel: DialogPanel

  init {
    val settings = ExternalDiffSettings.instance

    panel = panel {
      row {
        val diffEnabled = checkBox("Enable external diff tool", settings::isDiffEnabled)
        enableSubRowsIfSelected(diffEnabled.component)

        row("Path to executable:") {
          executableTextField(DiffBundle.message("select.external.diff.program.dialog.title"),
                              { settings.diffExePath }, { settings.diffExePath = it })
        }
        row("Parameters:") {
          textField(settings::diffParameters)
        }
        row {
          checkBox("Use by default", settings::isDiffDefault)
        }.largeGapAfter()
      }

      row {
        val mergeEnabled = checkBox("Enable external merge tool", settings::isMergeEnabled)
        enableSubRowsIfSelected(mergeEnabled.component)

        row("Path to executable:") {
          executableTextField(DiffBundle.message("select.external.merge.program.dialog.title"),
                              { settings.mergeExePath }, { settings.mergeExePath = it })
        }
        row("Parameters:") {
          textField(settings::mergeParameters)
        }
        row {
          checkBox("Trust process exit code", settings::isMergeTrustExitCode)
        }
      }

      commentRow(DESCRIPTION_TEXT)
    }
  }

  fun createComponent(): JComponent {
    return panel
  }

  fun isModified(): Boolean {
    return panel.isModified()
  }

  fun apply() {
    panel.apply()
  }

  fun reset() {
    panel.reset()
  }

  private fun Row.enableSubRowsIfSelected(button: AbstractButton): Row {
    subRowsEnabled = button.isSelected
    button.addChangeListener { subRowsEnabled = button.isSelected }
    return this
  }

  private fun Cell.executableTextField(title: String,
                                       modelGet: () -> String,
                                       modelSet: (String) -> Unit): CellBuilder<TextFieldWithBrowseButton> {
    val pathField = TextFieldWithBrowseButton()
    pathField.addBrowseFolderListener(title, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
    return pathField().withBinding(pathField::getText, pathField::setText, modelGet, modelSet)
  }
}
