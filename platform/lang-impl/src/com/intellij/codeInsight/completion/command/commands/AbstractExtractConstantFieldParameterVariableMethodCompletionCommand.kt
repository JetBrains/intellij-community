// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

abstract class AbstractExtractConstantCompletionCommandProvider :
  ActionCommandProvider(actionId = "IntroduceConstant",
                        presentableName = ActionsBundle.message("action.IntroduceConstant.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.IntroduceConstant.description"),
                        synonyms = listOf("Extract constant", "Introduce constant")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return findOffsetToCall(offset, psiFile) != null
  }

  abstract fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int?
}


abstract class AbstractExtractFieldCompletionCommandProvider :
  ActionCommandProvider(actionId = "IntroduceField",
                        presentableName = ActionsBundle.message("action.IntroduceField.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.IntroduceField.description"),
                        synonyms = listOf("Extract field", "Introduce field")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return findOffsetToCall(offset, psiFile) != null
  }

  abstract fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int?
}

abstract class AbstractExtractParameterCompletionCommandProvider :
  ActionCommandProvider(actionId = "IntroduceParameter",
                        presentableName = ActionsBundle.message("action.IntroduceParameter.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.IntroduceParameter.description"),
                        synonyms = listOf("Extract parameter", "Introduce parameter")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return findOffsetToCall(offset, psiFile) != null
  }

  abstract fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int?
}

abstract class AbstractExtractLocalVariableCompletionCommandProvider :
  ActionCommandProvider(actionId = "IntroduceVariable",
                        presentableName = ActionsBundle.message("action.IntroduceVariable.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.IntroduceVariable.description"),
                        synonyms = listOf("Extract local variable", "Introduce local variable")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return findOutermostExpression(offset, psiFile, editor) != null
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            synonyms = super.synonyms,
                                            presentableActionName = super.presentableName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText,
                                            highlightInfo = createHighlightingInfo(context.offset, context.psiFile, context.editor)) {
      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        if (editor == null) return
        val expression = findOutermostExpression(offset, psiFile, editor) ?: return
        editor.selectionModel.setSelection(expression.textRange.startOffset, expression.textRange.endOffset)
        super.execute(offset, psiFile, editor)
      }
    }
  }

  private fun createHighlightingInfo(offset: Int, psiFile: PsiFile, editor: Editor): HighlightInfoLookup? {
    val outermostExpression = findOutermostExpression(offset, psiFile, editor)
    if (outermostExpression != null) {
      return HighlightInfoLookup(outermostExpression.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
    }
    return null
  }

  protected abstract fun findOutermostExpression(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement?
}

abstract class AbstractExtractMethodCompletionCommandProvider(
  actionId: String,
  presentableName: @Nls String,
  previewText: @Nls String?,
  synonyms: List<String> = emptyList(),
) :
  ActionCommandProvider(
    actionId = actionId,
    presentableName = presentableName,
    icon = null,
    priority = -150,
    previewText = previewText,
    synonyms = synonyms,
  ) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val controlFlowStatement = findControlFlowStatement(offset, psiFile)
    if (controlFlowStatement != null) {
      editor?.selectionModel?.setSelection(controlFlowStatement.textRange.startOffset, controlFlowStatement.textRange.endOffset)
      return super.isApplicable(offset, psiFile, editor)
    }
    val outermostExpression = findOutermostExpression(offset, psiFile, editor)
    if (outermostExpression != null) {
      editor?.selectionModel?.setSelection(outermostExpression.textRange.startOffset, outermostExpression.textRange.endOffset)
      return super.isApplicable(offset, psiFile, editor)
    }
    return false
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand {
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            synonyms = super.synonyms,
                                            presentableActionName = super.presentableName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText,
                                            highlightInfo = createHighlightingInfo(context.offset, context.psiFile, context.editor)) {
      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        if (editor == null) return
        val controlFlowStatement = findControlFlowStatement(offset, psiFile)
        if (controlFlowStatement != null) {
          editor.selectionModel.setSelection(controlFlowStatement.textRange.startOffset, controlFlowStatement.textRange.endOffset)
          super.execute(offset, psiFile, editor)
          return
        }
        val expression = findOutermostExpression(offset, psiFile, editor)
        if (expression != null) {
          editor.selectionModel.setSelection(expression.textRange.startOffset, expression.textRange.endOffset)
          super.execute(offset, psiFile, editor)
        }
      }
    }
  }

  private fun createHighlightingInfo(offset: Int, psiFile: PsiFile, editor: Editor): HighlightInfoLookup? {
    val controlFlowStatement = findControlFlowStatement(offset, psiFile)
    if (controlFlowStatement != null) {
      return HighlightInfoLookup(controlFlowStatement.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
    }
    val outermostExpression = findOutermostExpression(offset, psiFile, editor)
    if (outermostExpression != null) {
      return HighlightInfoLookup(outermostExpression.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
    }
    return null
  }

  protected abstract fun findOutermostExpression(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement?

  protected abstract fun findControlFlowStatement(offset: Int, psiFile: PsiFile): PsiElement?
}
