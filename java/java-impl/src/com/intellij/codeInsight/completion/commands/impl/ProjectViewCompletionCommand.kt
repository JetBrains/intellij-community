// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.ide.actions.ActivateToolWindowAction.Manager
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace

class ProjectViewCompletionCommand : AbstractActionCompletionCommand(Manager.getActionIdForToolWindow("Project"),
                                                                     "Project tool",
                                                                     JavaBundle.message("command.completion.project.tool.text"),
                                                                     null,
                                                                     -150) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (offset - 1 < 0) return true
    val element = psiFile.findElementAt(offset - 1)
    if (element is PsiComment || element is PsiWhiteSpace) return true
    val ch = psiFile.fileDocument.immutableCharSequence[offset - 1]
    if (!ch.isLetterOrDigit() && ch != ']' && ch != ')') return true
    return false
  }
}