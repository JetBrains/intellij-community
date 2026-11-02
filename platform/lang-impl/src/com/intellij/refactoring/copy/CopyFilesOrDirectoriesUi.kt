// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.copy

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessors
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.EditorTextField
import com.intellij.ui.RecentsManager
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JLabel
import javax.swing.event.DocumentEvent

internal class CopyFilesOrDirectoriesUi(
  project: Project,
  showNewNameField: Boolean,
  showDirectoryField: Boolean,
  onChange: Runnable,
) {

  lateinit var informationLabel: JLabel

  var newNameField: EditorTextField? = null
    private set

  var targetDirectoryField: TextFieldWithHistoryWithBrowseButton? = null
    private set

  val panel = panel {
    row {
      informationLabel = label("")
        .bold()
        .component
    }

    if (showNewNameField) {
      row(RefactoringBundle.message("copy.files.new.name.label")) {
        newNameField = cell(EditorTextField())
          .align(AlignX.FILL)
          .applyToComponent {
            addDocumentListener(object : DocumentListener {
              override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                onChange.run()
              }
            })
          }
          .component
      }
    }

    if (showDirectoryField) {
      row(RefactoringBundle.message("copy.files.to.directory.label")) {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
          .withTitle(RefactoringBundle.message("select.target.directory"))
          .withDescription(RefactoringBundle.message("the.file.will.be.copied.to.this.directory"))
        val shortcutText = KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION))

        targetDirectoryField = cell(TextFieldWithHistoryWithBrowseButton())
          .align(AlignX.FILL)
          .comment(RefactoringBundle.message("path.completion.shortcut", shortcutText))
          .applyToComponent {
            setTextFieldPreferredWidth(CopyFilesOrDirectoriesDialog.MAX_PATH_LENGTH)
            childComponent.isUsePreferredSizeAsMinimum = false
            RecentsManager.getInstance(project).getRecentEntries(CopyFilesOrDirectoriesDialog.RECENT_KEYS)?.let {
              childComponent.history = it
            }
            addBrowseFolderListener(project, descriptor, TextComponentAccessors.TEXT_FIELD_WITH_HISTORY_WHOLE_TEXT)
            childComponent.addDocumentListener(object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                onChange.run()
              }
            })
          }
          .component
      }
    }
  }
}
