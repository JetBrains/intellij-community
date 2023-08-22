// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.actions

import com.intellij.codeInsight.actions.ReaderModeMatcher
import com.intellij.codeInsight.actions.ReaderModeProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class DiffReaderModeMatcher : ReaderModeMatcher {
  override fun matches(project: Project, file: VirtualFile, editor: Editor?, mode: ReaderModeProvider.ReaderMode) =
    if (editor?.editorKind == EditorKind.DIFF) false else null
}