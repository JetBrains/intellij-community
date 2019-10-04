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

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.merge.MergeResult
import com.intellij.diff.merge.ThreesideMergeRequest
import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.external.ExternalDiffToolUtil
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.text.StringUtil
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
        }
        row {
          cell {
            button("Test Diff") { showTestDiff() }
            button("Test Three-Side Diff") { showTestThreeDiff() }
          }
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
        row {
          button("Test Merge") { showTestMerge() }
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
    return pathField().withBinding(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, PropertyBinding(modelGet, modelSet))
  }

  private fun showTestDiff() {
    panel.apply()

    try {
      val factory = DiffContentFactory.getInstance()
      val contents = listOf(factory.create("Left file content"), factory.create("Right file content"))
      val titles = listOf("Left.txt", "Right.txt")
      ExternalDiffToolUtil.execute(null, ExternalDiffSettings.instance, contents, titles, null)
    }
    catch (e: Exception) {
      Messages.showErrorDialog(e.message, "Can't Show Diff")
    }
  }

  private fun showTestThreeDiff() {
    panel.apply()

    try {
      val factory = DiffContentFactory.getInstance()
      val contents = listOf(factory.create("Left file content"), factory.create("Base file content"), factory.create("Right file content"))
      val titles = listOf("Left.txt", "Base.txt", "Right.txt")
      ExternalDiffToolUtil.execute(null, ExternalDiffSettings.instance, contents, titles, null)
    }
    catch (e: Exception) {
      Messages.showErrorDialog(e.message, "Can't Show Diff")
    }
  }

  private fun showTestMerge() {
    panel.apply()

    try {
      val factory = DiffRequestFactory.getInstance()
      val document = DocumentImpl("Original output file content")

      val callback = { result: MergeResult ->
        val message = when (result) {
          MergeResult.CANCEL -> "Merge conflict resolve was canceled."
          else -> "Merge conflict resolve successful.\nResolved content is:\n" +
                  StringUtil.shortenPathWithEllipsis(document.text, 60)
        }
        Messages.showInfoMessage(panel, message, "Test Complete")
      }
      val contents = listOf("Left file content", "Base file content", "Right file content")
      val titles = listOf("Left.txt", "Base.txt", "Right.txt")
      val request = factory.createMergeRequest(null, PlainTextFileType.INSTANCE, document, contents, null, titles, callback)
      ExternalDiffToolUtil.executeMerge(null, ExternalDiffSettings.instance, request as ThreesideMergeRequest)
    }
    catch (e: Exception) {
      Messages.showErrorDialog(e.message, "Can't Show Merge")
    }
  }
}
