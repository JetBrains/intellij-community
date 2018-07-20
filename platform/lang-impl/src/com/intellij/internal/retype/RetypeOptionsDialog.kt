// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.ide.util.propComponentProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JRadioButton
import javax.swing.JTextField

/**
 * @author yole
 */
class RetypeOptionsDialog(project: Project, private val haveCurrentFile: Boolean) : DialogWrapper(project) {
  var retypeDelay: Int by propComponentProperty(project, 400)
    private set
  var threadDumpDelay: Int by propComponentProperty(project, 100)
    private set
  var fileCount: Int by propComponentProperty(project, 10)
    private set
  var retypeExtension: String by propComponentProperty(project, "")
    private set

  private val typeDelaySpinner = JBIntSpinner(retypeDelay,0, 5000, 50)
  private val threadDumpDelaySpinner = JBIntSpinner(threadDumpDelay,50, 5000, 50)
  private val retypeCurrentFile = JRadioButton("Retype current file")
  private val retypeRandomFiles = JRadioButton("Retype")
  private val fileCountSpinner = JBIntSpinner(fileCount, 1, 5000)
  private val extensionTextField = JTextField(retypeExtension,5)

  init {
    init()
    title = "Retype Options"
  }

  override fun createCenterPanel(): JComponent {
    retypeCurrentFile.isEnabled = haveCurrentFile
    if (haveCurrentFile) {
      retypeCurrentFile.isSelected = true
    }
    else {
      retypeRandomFiles.isSelected = true
    }
    updateEnabledState()
    return panel {
      row(label = JLabel("Typing delay:")) {
        typeDelaySpinner()
      }
      row(label = JLabel("Thread dump capture delay:")) {
        threadDumpDelaySpinner()
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
    }
  }

  private fun updateEnabledState() {
    fileCountSpinner.isEnabled = retypeRandomFiles.isSelected
    extensionTextField.isEnabled = retypeRandomFiles.isSelected
  }

  override fun doOKAction() {
    retypeDelay = typeDelaySpinner.number
    threadDumpDelay = threadDumpDelaySpinner.number
    fileCount = fileCountSpinner.number
    retypeExtension = extensionTextField.text

    super.doOKAction()
  }

  val isRetypeCurrentFile get() = retypeCurrentFile.isSelected
}
