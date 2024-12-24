// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.analysis.AnalysisBundle.message
import com.intellij.codeInsight.completion.commands.api.getTargetContext
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil

class RenameActionCompletionCommand : AbstractActionCompletionCommand(IdeActions.ACTION_RENAME,
                                                                      "Rename identifier",
                                                                      ActionsBundle.message("action.RenameElement.text"),
                                                                      null) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (super.isApplicable(offset, psiFile, editor)) return true
    val result = getTargetElements(editor, psiFile, offset)
    return result?.isNotEmpty() == true
  }

  private fun getTargetElements(
    editor: Editor?,
    psiFile: PsiFile,
    offset: Int,
  ): Map<PsiElement, Int>? {
    if (editor == null) return null
    val targetOffsets = mutableListOf<Int>()
    val fileDocument = psiFile.fileDocument
    val lineNumber = fileDocument.getLineNumber(offset)
    val lineStartOffset = fileDocument.getLineStartOffset(lineNumber)
    val targetTextRange = TextRange(lineStartOffset, offset)
    PsiTreeUtil.processElements(psiFile, object : PsiElementProcessor<PsiElement> {
      override fun execute(element: PsiElement): Boolean {
        if (targetTextRange.contains(element.textRange)) {
          targetOffsets.add(element.textRange.startOffset)
        }
        if (element.textRange.startOffset >= offset) return false
        return true
      }
    })
    targetOffsets.add(offset)
    val result = mutableMapOf<PsiElement, Int>()

    for (targetOffset in targetOffsets) {
      val context = getTargetContext(targetOffset, editor)
      if (context == null) continue
      if (!context.isWritable || context !is PsiNamedElement) continue
      result.put(context, targetOffset)
    }
    return result
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val elementsInLine = runWithModalProgressBlocking(psiFile.project, message("scanning.scope.progress.title")) {
      if (super.isApplicable(offset, psiFile, editor)) {
        return@runWithModalProgressBlocking mapOf(Pair(offset, null))
      }
      else {
        val targetElements = getTargetElements(editor, psiFile, offset)
        if (targetElements == null) return@runWithModalProgressBlocking mutableMapOf<Int, PsiElement>()
        val elementsInLine: Map<Int, PsiElement> = targetElements.map { it.value }.mapNotNull {
          val first = psiFile.findElementAt(if (it > 0) it - 1 else it)
          if (first == null) null
          else
            Pair(it, first)
        }
          .toMap()
        return@runWithModalProgressBlocking elementsInLine
      }
    }
    if (elementsInLine.size == 1) {
      val offset = elementsInLine.keys.first()
      handle(offset, psiFile, editor)
      return
    }
    val reversedMap = elementsInLine.mapNotNull {
      val first = it.value
      if (first == null) return@mapNotNull null
      Pair(first, it.key)
    }.toMap()
    val elements: List<PsiElement> = reversedMap.keys.toList().toList()
    val highlighter = ScopeHighlighter(editor)

    val navigator = PsiTargetNavigator<PsiElement>(elements)
      .presentationProvider { element ->
        TargetPresentation.builder(element.text)
          .presentation()
      }
      .builderConsumer { builder ->
        builder.setItemSelectedCallback { e ->
          if (e == null) return@setItemSelectedCallback
          highlighter.dropHighlight()
          val element = e.dereference()
          if (element == null) return@setItemSelectedCallback
          highlighter.highlight(element, listOf(element))
        }
      }
    var popup = navigator.createPopup(psiFile.project,
                                      ActionsBundle.message("action.RenameAction.text"),
                                      object : PsiElementProcessor<PsiElement> {
                                        override fun execute(element: PsiElement): Boolean {
                                          handle(element.textRange.endOffset, psiFile, editor)
                                          return true
                                        }
                                      })
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        highlighter.dropHighlight()
      }
    })
    popup.showInBestPositionFor(editor)
  }

  private fun handle(offset: Int, psiFile: PsiFile, editor: Editor) {
    editor.caretModel.moveToOffset(offset)
    super.execute(offset, psiFile, editor)
  }
}