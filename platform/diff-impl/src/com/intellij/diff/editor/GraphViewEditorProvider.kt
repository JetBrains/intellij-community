package com.intellij.diff.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

class GraphViewEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is VCSContentVirtualFile
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    return VCSContentEditor(file as VCSContentVirtualFile)
  }

  override fun disposeEditor(editor: FileEditor) {
    Disposer.dispose(editor)
  }

  override fun getEditorTypeId(): String = "GraphViewEditor"
  override fun getPolicy(): FileEditorPolicy =
    FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
}