// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.ide.util.propComponentProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * @author yole
 */
class RetypeOptionsDialog(project: Project) : DialogWrapper(project) {
  var retypeDelay: Int by propComponentProperty(project, 400)
    private set
  var threadDumpDelay: Int by propComponentProperty(project, 100)
    private set

  private val typeDelaySpinner = JBIntSpinner(retypeDelay,0, 5000, 50)
  private val threadDumpDelaySpinner = JBIntSpinner(threadDumpDelay,50, 5000, 50)

  init {
    init()
    title = "Retype Options"
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(label = JLabel("Typing delay:")) {
        typeDelaySpinner()
      }
      row(label = JLabel("Thread dump capture delay:")) {
        threadDumpDelaySpinner()
      }
    }
  }

  override fun doOKAction() {
    retypeDelay = typeDelaySpinner.number
    threadDumpDelay = threadDumpDelaySpinner.number

    super.doOKAction()
  }
}
