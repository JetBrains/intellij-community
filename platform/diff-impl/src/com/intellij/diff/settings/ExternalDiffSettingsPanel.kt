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
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.layout.*
import javax.swing.AbstractButton
import javax.swing.JComponent

class ExternalDiffSettingsPanel {
  private val panel: DialogPanel

  init {
    val settings = ExternalDiffSettings.instance

    panel = panel {
      blockRow {
        val diffEnabled = checkBox(DiffBundle.message("settings.external.diff.enable.external.diff.tool"), settings::isDiffEnabled)
        enableSubRowsIfSelected(diffEnabled.component)

        row {
          row(DiffBundle.message("settings.external.diff.path.to.executable")) {
            executableTextField(DiffBundle.message("select.external.diff.program.dialog.title"),
                                { settings.diffExePath }, { settings.diffExePath = it })
          }
          row(DiffBundle.message("settings.external.diff.parameters")) {
            textField(settings::diffParameters)
          }
          row {
            checkBox(DiffBundle.message("settings.external.diff.use.by.default"), settings::isDiffDefault)
          }
          row {
            cell(isFullWidth = true) {
              button(DiffBundle.message("settings.external.diff.test.diff")) { showTestDiff() }
              button(DiffBundle.message("settings.external.diff.test.three.side.diff")) { showTestThreeDiff() }
                .withLargeLeftGap()
            }
          }
        }
      }

      blockRow {
        val mergeEnabled = checkBox(DiffBundle.message("settings.external.diff.enable.external.merge.tool"), settings::isMergeEnabled)
        enableSubRowsIfSelected(mergeEnabled.component)

        row {
          row(DiffBundle.message("settings.external.diff.path.to.executable.merge")) {
            executableTextField(DiffBundle.message("select.external.merge.program.dialog.title"),
                                { settings.mergeExePath }, { settings.mergeExePath = it })
          }
          row(DiffBundle.message("settings.external.diff.parameters.merge")) {
            textField(settings::mergeParameters)
          }
          row {
            checkBox(DiffBundle.message("settings.external.diff.trust.process.exit.code"), settings::isMergeTrustExitCode)
          }
          row {
            cell(isFullWidth = true) {
              button(DiffBundle.message("settings.external.diff.test.merge")) { showTestMerge() }
            }
          }
        }
      }

      row {
        comment(DiffBundle.message("settings.diff.tools.parameters"))
      }
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
    button.addChangeListener {
      subRowsEnabled = button.isSelected
      button.parent?.repaint() // Repaint all dependent components in sync
    }
    return this
  }

  private fun Cell.executableTextField(title: @NlsContexts.DialogTitle String,
                                       modelGet: () -> String,
                                       modelSet: (String) -> Unit): CellBuilder<TextFieldWithBrowseButton> {
    val pathField = TextFieldWithBrowseButton()
    pathField.addBrowseFolderListener(title, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
    return pathField().withBinding(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText,
                                   PropertyBinding(modelGet, modelSet))
  }

  private fun showTestDiff() {
    panel.apply()

    try {
      val factory = DiffContentFactory.getInstance()
      val contents = listOf(factory.create(DiffBundle.message("settings.external.diff.left.file.content")),
                            factory.create(DiffBundle.message("settings.external.diff.right.file.content")))
      val titles = listOf("Left.txt", "Right.txt")
      ExternalDiffToolUtil.execute(null, ExternalDiffSettings.instance, contents, titles, null)
    }
    catch (e: Exception) {
      Messages.showErrorDialog(e.message, DiffBundle.message("error.cannot.show.diff"))
    }
  }

  private fun showTestThreeDiff() {
    panel.apply()

    try {
      val factory = DiffContentFactory.getInstance()
      val contents = listOf(factory.create(DiffBundle.message("settings.external.diff.left.file.content")),
                            factory.create(DiffBundle.message("settings.external.diff.base.file.content")),
                            factory.create(DiffBundle.message("settings.external.diff.right.file.content")))
      val titles = listOf("Left.txt", "Base.txt", "Right.txt")
      ExternalDiffToolUtil.execute(null, ExternalDiffSettings.instance, contents, titles, null)
    }
    catch (e: Exception) {
      Messages.showErrorDialog(e.message, DiffBundle.message("error.cannot.show.diff"))
    }
  }

  private fun showTestMerge() {
    panel.apply()

    try {
      val factory = DiffRequestFactory.getInstance()
      val document = DocumentImpl(DiffBundle.message("settings.external.diff.original.output.file.content"))

      val callback = { result: MergeResult ->
        val message = when (result) {
          MergeResult.CANCEL -> DiffBundle.message("settings.external.diff.merge.conflict.resolve.was.canceled")
          else -> DiffBundle.message("settings.external.diff.merge.conflict.resolve.successful",
                                     StringUtil.shortenPathWithEllipsis(document.text, 60))

        }
        Messages.showInfoMessage(panel, message, DiffBundle.message("settings.external.diff.test.complete"))
      }
      val contents = listOf(DiffBundle.message("settings.external.diff.left.file.content"),
                            DiffBundle.message("settings.external.diff.base.file.content"),
                            DiffBundle.message("settings.external.diff.right.file.content"))
      val titles = listOf("Left.txt", "Base.txt", "Right.txt")
      val request = factory.createMergeRequest(null, PlainTextFileType.INSTANCE, document, contents, null, titles, callback)
      ExternalDiffToolUtil.executeMerge(null, ExternalDiffSettings.instance, request as ThreesideMergeRequest, panel)
    }
    catch (e: Exception) {
      Messages.showErrorDialog(e.message, DiffBundle.message("error.cannot.show.merge"))
    }
  }
}
