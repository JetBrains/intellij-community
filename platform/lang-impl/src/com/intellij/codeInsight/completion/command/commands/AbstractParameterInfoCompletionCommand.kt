// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * A command that executes the "Parameter Info" action
 */
abstract class AbstractParameterInfoCompletionCommand :
  ActionCommandProvider(actionId = "ParameterInfo",
                        synonyms = listOf("Parameter info", "Show parameters"),
                        presentableName = ActionsBundle.message("action.ParameterInfo.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.ParameterInfo.description")) {

  final override fun supportsReadOnly(): Boolean = true
  final override fun supportsInjected(): Boolean = false
  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    return super.isApplicable(offset, psiFile, editor) && inParameterList(offset, psiFile)
  }

  abstract fun inParameterList(offset: Int, psiFile: PsiFile): Boolean
}