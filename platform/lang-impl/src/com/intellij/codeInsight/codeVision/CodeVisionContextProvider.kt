package com.intellij.codeInsight.codeVision

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

open class CodeVisionContextProvider(val project: Project) {
  open fun createCodeVisionContext(editor: Editor): EditorCodeVisionContext {
    return EditorCodeVisionContext(CodeVisionInitializer.getInstance(project).getCodeVisionHost(), editor)
  }
}