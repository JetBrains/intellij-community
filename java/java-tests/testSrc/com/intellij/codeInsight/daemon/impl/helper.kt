// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal fun openTextEditorForDaemonTest(project: Project, file: VirtualFile): TextEditor? {
  return runWithModalProgressBlocking(project, "") {
    FileEditorManagerEx.getInstanceEx(project).openFile(file, FileEditorOpenOptions())
  }.allEditors.firstOrNull { it is TextEditor } as? TextEditor?
}