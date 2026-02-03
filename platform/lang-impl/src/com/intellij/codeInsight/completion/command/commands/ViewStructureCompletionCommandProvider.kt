// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.ide.actions.ViewStructureAction
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil

class ViewStructureCompletionCommandProvider : ActionCommandProvider(actionId = "FileStructurePopup", synonyms = listOf("File Structure", "Go to members", "Show structure"), presentableName = CodeInsightBundle.message("command.completion.view.structure.text"), priority = -150, previewText = ActionsBundle.message("action.FileStructurePopup.description")) {

  override fun supportNewLineCompletion(): Boolean = true

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand {
    return object : ActionCompletionCommand(actionId = super.actionId, presentableActionName = super.presentableName, icon = super.icon, priority = super.priority, previewText = super.previewText, synonyms = super.synonyms) {
      override val action: AnAction
        get() = ViewStructureAction { node: AbstractTreeNode<*> ->
          val psiTreeElementBase = node.getValue() as? PsiTreeElementBase<*> ?: return@ViewStructureAction
          val psiElement = psiTreeElementBase.element ?: return@ViewStructureAction
          if (!psiElement.isPhysical) return@ViewStructureAction
          if (!psiElement.isValid) return@ViewStructureAction
          if (psiElement !is PsiNameIdentifierOwner) return@ViewStructureAction
          val endOffset = psiElement.nameIdentifier?.textRange?.endOffset ?: return@ViewStructureAction
          val project = context.project
          val selectedTextEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@ViewStructureAction
          val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(selectedTextEditor.document) ?: return@ViewStructureAction
          if (!PsiTreeUtil.isAncestor(psiFile, psiElement, false)) return@ViewStructureAction
          selectedTextEditor.caretModel.moveToOffset(endOffset)
        }
    }
  }
}