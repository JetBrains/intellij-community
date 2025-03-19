// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.ide.util.propComponentProperty
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent


class RetypeOptions(val project: Project) {
  var retypeDelay: Int by propComponentProperty(project, 400)
  var threadDumpDelay: Int by propComponentProperty(project, 100)
  var enableLargeIndexing: Boolean by propComponentProperty(project, false)
  var largeIndexFilesCount: Int by propComponentProperty(project, 50_000)
  var recordScript: Boolean by propComponentProperty(project, true)
  var fileCount: Int by propComponentProperty(project, 10)
  var retypeExtension: String by propComponentProperty(project, "")
  var restoreOriginalText: Boolean by propComponentProperty(project, true)
  var retypeCurrentFile: Boolean = false
}

@ApiStatus.Internal
class RetypeOptionsDialog(project: Project, private val retypeOptions: RetypeOptions, private val editor: Editor?) : DialogWrapper(project) {
  init {
    init()
    title = "Retype Options"
  }

  override fun createCenterPanel(): JComponent {
    retypeOptions.retypeCurrentFile = editor != null

    return panel {
      row("Typing delay (ms):") {
        spinner(0..5000, 50)
          .bindIntValue(retypeOptions::retypeDelay)
      }
      row("Thread dump capture delay (ms):") {
        spinner(50..5000, 50)
          .bindIntValue(retypeOptions::threadDumpDelay)
      }
      row {
        val c = checkBox("Create")
          .bindSelected(retypeOptions::enableLargeIndexing)
          .gap(RightGap.SMALL)
        spinner(100..1_000_000, 1_000)
          .bindIntValue(retypeOptions::largeIndexFilesCount)
          .gap(RightGap.SMALL)
          .enabledIf(c.selected)
        label("files to start background indexing")
      }
      buttonsGroup {
        row {
          radioButton(if (editor?.selectionModel?.hasSelection() == true) "Retype selected text" else "Retype current file", true)
            .enabled(editor != null)
        }.topGap(TopGap.SMALL)
        row {
          val r = radioButton("Retype", false)
            .gap(RightGap.SMALL)
          spinner(1..5000)
            .bindIntValue(retypeOptions::fileCount)
            .gap(RightGap.SMALL)
            .enabledIf(r.selected)
          label("files with different sizes and extension")
            .gap(RightGap.SMALL)
          textField()
            .bindText(retypeOptions::retypeExtension)
            .columns(5)
            .enabledIf(r.selected)
        }.bottomGap(BottomGap.SMALL)
      }.bind(retypeOptions::retypeCurrentFile)
      row {
        checkBox("Record script for performance testing plugin")
          .bindSelected(retypeOptions::recordScript)
      }
      row {
        checkBox("Restore original text after retype")
          .bindSelected(retypeOptions::restoreOriginalText)
      }
    }
  }
}
