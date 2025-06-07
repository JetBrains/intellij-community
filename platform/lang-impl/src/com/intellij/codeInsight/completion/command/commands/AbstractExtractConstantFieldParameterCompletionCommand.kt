// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

abstract class AbstractExtractConstantCompletionCommandProvider :
  ActionCommandProvider(actionId = "IntroduceConstant",
                        name = "Introduce constant",
                        i18nName = ActionsBundle.message("action.IntroduceConstant.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.IntroduceConstant.description"),
                        synonyms = listOf("Extract constant")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return findOffsetToCall(offset, psiFile) != null
  }

  abstract fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int?
}


abstract class AbstractExtractFieldCompletionCommandProvider :
  ActionCommandProvider(actionId = "IntroduceField",
                        name = "Introduce field",
                        i18nName = ActionsBundle.message("action.IntroduceField.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.IntroduceField.description"),
                        synonyms = listOf("Extract field")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return findOffsetToCall(offset, psiFile) != null
  }

  abstract fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int?
}

abstract class AbstractExtractParameterCompletionCommandProvider :
  ActionCommandProvider(actionId = "IntroduceParameter",
                        name = "Introduce parameter",
                        i18nName = ActionsBundle.message("action.IntroduceParameter.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.IntroduceParameter.description"),
                        synonyms = listOf("Extract parameter")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return findOffsetToCall(offset, psiFile) != null
  }

  abstract fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int?
}
