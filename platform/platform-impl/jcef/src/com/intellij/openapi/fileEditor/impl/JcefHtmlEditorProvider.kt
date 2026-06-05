// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class JcefHtmlEditorProvider : FileEditorProvider, DumbAware {
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    require(file is HTMLVirtualFile) {
      "cannot create html editor for non-html file, actual $file"
    }
    require(!file.isDisposed()) {
      "html request is already disposed"
    }
    return if (file.shouldUseMockEditor()) {
      HTMLFileEditorMock(file)
    }
    else {
      HTMLFileEditorImpl(project, file, file.htmlRequest)
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return file is HTMLVirtualFile && !file.isDisposed() && (file.shouldUseMockEditor() || JBCefApp.isSupported())
  }

  override fun acceptRequiresReadAction(): Boolean = false

  override fun getEditorTypeId(): String = "html-editor"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
