// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.ide.util.propComponentProperty
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JRadioButton
import javax.swing.JTextField

/**
 * @author yole
 */
class RetypeOptionsDialog(project: Project, private val editor: Editor?) : DialogWrapper(project) {
  var retypeDelay: Int by propComponentProperty(project, 400)
    private set
  var threadDumpDelay: Int by propComponentProperty(project, 100)
    private set
  var largeIndexFilesCount: Int by propComponentProperty(project, 50_000)
    private set
  var enableLargeIndexing: Boolean by propComponentProperty(project, false)
    private set
  var fileCount: Int by propComponentProperty(project, 10)
    private set
  var retypeExtension: String by propComponentProperty(project, "")
    private set
  var recordScript: Boolean by propComponentProperty(project, true)
    private set
  var restoreOriginalText: Boolean by propComponentProperty(project, true)
    private set

  private val typeDelaySpinner = JBIntSpinner(retypeDelay, 0, 5000, 50)
  private val threadDumpDelaySpinner = JBIntSpinner(threadDumpDelay, 50, 5000, 50)

  private val largeIndexFileCountSpinner = JBIntSpinner(largeIndexFilesCount, 100, 1_000_000, 1_000)
  private val largeIndexEnabled = JBCheckBox(null, enableLargeIndexing)

  private val retypeCurrentFile = JRadioButton(
    if (editor?.selectionModel?.hasSelection() == true) "Retype selected text" else "Retype current file")
  private val retypeRandomFiles = JRadioButton("Retype")
  private val fileCountSpinner = JBIntSpinner(fileCount, 1, 5000)
  private val extensionTextField = JTextField(retypeExtension, 5)
  private val recordCheckBox = CheckBox("Record script for performance testing plugin", true)
  private val restoreTextBox = JBCheckBox("Restore original text after retype", restoreOriginalText)

  init {
    init()
    title = "Retype Options"

    largeIndexEnabled.addItemListener {
      largeIndexFileCountSpinner.isEnabled = it.stateChange == ItemEvent.SELECTED
    }
    largeIndexFileCountSpinner.isEnabled = largeIndexEnabled.isSelected
  }

  override fun createCenterPanel(): JComponent {
    retypeCurrentFile.isEnabled = editor != null
    if (editor != null) {
      retypeCurrentFile.isSelected = true
    }
    else {
      retypeRandomFiles.isSelected = true
    }
    updateEnabledState()
    return panel {
      row(label = JLabel("Typing delay (ms):")) {
        typeDelaySpinner()
      }
      row(label = JLabel("Thread dump capture delay (ms):")) {
        threadDumpDelaySpinner()
      }
      row(label = JLabel("Create files for start background index (count of files):")) {
        largeIndexFileCountSpinner()
        largeIndexEnabled()
      }
      buttonGroup(::updateEnabledState) {
        row {
          retypeCurrentFile()
        }
        row {
          retypeRandomFiles()
          fileCountSpinner()
          label("files with different sizes and extension")
          extensionTextField()
        }
      }
      row {
        recordCheckBox()
      }
      row {
        restoreTextBox()
      }
    }
  }

  private fun updateEnabledState() {
    fileCountSpinner.isEnabled = retypeRandomFiles.isSelected
    extensionTextField.isEnabled = retypeRandomFiles.isSelected
  }

  @Suppress("Duplicates")
  override fun doOKAction() {
    retypeDelay = typeDelaySpinner.number
    threadDumpDelay = threadDumpDelaySpinner.number
    fileCount = fileCountSpinner.number
    retypeExtension = extensionTextField.text
    recordScript = recordCheckBox.isSelected

    largeIndexFilesCount = largeIndexFileCountSpinner.number
    enableLargeIndexing = largeIndexEnabled.isSelected

    restoreOriginalText = restoreTextBox.isSelected

    super.doOKAction()
  }

  val isRetypeCurrentFile get() = retypeCurrentFile.isSelected
}
