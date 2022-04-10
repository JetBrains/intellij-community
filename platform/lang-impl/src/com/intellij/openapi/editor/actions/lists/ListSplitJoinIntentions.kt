// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions.lists

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

abstract class SplitJoinIntention : PsiElementBaseIntentionAction(), LowPriorityAction {
  companion object {
    fun extractDocument(project: Project, editor: Editor?, element: PsiElement): Document? {
      val documentManager = PsiDocumentManager.getInstance(project)
      return editor?.document ?: documentManager.getDocument(element.containingFile)
    }
    
    val logger = Logger.getInstance(SplitJoinIntention::class.java)
  }

  protected abstract fun operation(): JoinOrSplit

  @IntentionName
  protected abstract fun getIntentionText(splitJoinContext: ListSplitJoinContext, data: ListWithElements): String
  
  protected abstract fun getReplacements(splitJoinContext: ListSplitJoinContext, data: ListWithElements): List<Pair<TextRange, String>>

  final override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    val (splitJoinContext, data) = getListSplitJoinContext(project, editor, element) ?: return false
    text = getIntentionText(splitJoinContext, data)
    return true
  }

  final override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    val (splitJoinContext, data) = getListSplitJoinContext(project, editor, element) ?: return

    val list = data.list
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = extractDocument(project, editor, element) ?: return
    val marker = document.createRangeMarker(list.textRange)
    val containingFile = element.containingFile

    val replacements: List<Pair<TextRange, String>> = getReplacements(splitJoinContext, data)
    for ((range, text) in replacements.reversed()) {
      document.replaceString(range.startOffset, range.endOffset, text)
    }
    documentManager.commitDocument(document)
    if (marker.isValid) {
      splitJoinContext.reformatRange(containingFile, TextRange.create(marker), operation())
    }
  }
  
  protected open fun validateOrder(replacements: List<Pair<TextRange, String>>) {
    var prev: TextRange? = null
    for ((range, _) in replacements) {
      if (prev != null) {
        if (range.startOffset < prev.endOffset) {
          logger.error("Incorrect replacements order. The ranges must be sorted")
          break
        }  
      }
      prev = range
    }
  }

  protected open fun getListSplitJoinContext(project: Project,
                                             editor: Editor?,
                                             element: PsiElement): Pair<ListSplitJoinContext, ListWithElements>? =
    getListSplitJoinContext(element, operation())
}

open class SplitLineIntention : SplitJoinIntention() {
  override fun getFamilyName(): String = CodeInsightBundle.message("intention.family.name.split.values")
  override fun operation(): JoinOrSplit = JoinOrSplit.SPLIT

  override fun getIntentionText(splitJoinContext: ListSplitJoinContext, data: ListWithElements): String =
    splitJoinContext.getSplitText(data)

  override fun getReplacements(splitJoinContext: ListSplitJoinContext, data: ListWithElements) =
    splitJoinContext.getReplacementsForSplitting(data)
}

open class JoinLinesIntention : SplitJoinIntention() {
  override fun getFamilyName(): String = CodeInsightBundle.message("intention.family.name.join.values")
  override fun operation(): JoinOrSplit = JoinOrSplit.JOIN

  override fun getIntentionText(splitJoinContext: ListSplitJoinContext, data: ListWithElements): String =
    splitJoinContext.getJoinText(data)

  override fun getReplacements(splitJoinContext: ListSplitJoinContext, data: ListWithElements) =
    splitJoinContext.getReplacementsForJoining(data)
}