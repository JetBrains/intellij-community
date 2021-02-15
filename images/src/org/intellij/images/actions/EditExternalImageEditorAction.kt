// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.layout.*
import org.intellij.images.ImagesBundle
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
class EditExternalImageEditorAction: DumbAwareAction() {
  companion object {
    const val EXT_PATH_KEY = "Images.ExternalEditorPath"

    fun showDialog(project: Project?) {
      EditExternalImageEditorDialog(project).show()
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    showDialog(e.project)
  }

  class EditExternalImageEditorDialog(val project: Project?): DialogWrapper(project) {

    init {
      title = ImagesBundle.message("edit.external.editor.path.dialog.title")
      setOKButtonText(IdeBundle.message("button.save"))
      init()
    }

    override fun createCenterPanel(): JComponent {
      val fileDescriptor = FileChooserDescriptor(true, SystemInfo.isMac, false, false, false, false)
      fileDescriptor.isShowFileSystemRoots = true
      fileDescriptor.title = ImagesBundle.message("select.external.executable.title")
      fileDescriptor.description = ImagesBundle.message("select.external.executable.message")

      return panel() {
        row(ImagesBundle.message("external.editor.executable.path")) {
          textFieldWithBrowseButton({PropertiesComponent.getInstance().getValue(EXT_PATH_KEY, "")},
                                    {PropertiesComponent.getInstance().setValue(EXT_PATH_KEY, it)},
                                    project = project,
                                    fileChooserDescriptor = fileDescriptor,
                                    fileChosen = {it.path})
        }
      }
    }
  }
}