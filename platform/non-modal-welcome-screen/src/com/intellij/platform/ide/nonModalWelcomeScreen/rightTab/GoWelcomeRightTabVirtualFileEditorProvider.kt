package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NonNls

class GoWelcomeRightTabVirtualFileEditorProvider : FileEditorProvider, DumbAware {
  companion object {
    const val ID: String = "GoNewProjectWindowFileEditor"
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is GoWelcomeRightTabVirtualFile
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val file = file as GoWelcomeRightTabVirtualFile
    return GoWelcomeRightTabVirtualFileEditor(file)
  }

  override fun getEditorTypeId(): @NonNls String = ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_OTHER_EDITORS

  override fun isDumbAware(): Boolean = true
}