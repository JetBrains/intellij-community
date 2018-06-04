// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.tooltips.TooltipActionProvider
import com.intellij.codeInsight.intention.AbstractEmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.TooltipAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiDocumentManager
import com.intellij.xml.util.XmlStringUtil
import java.util.*

class DaemonTooltipActionProvider : TooltipActionProvider {
  override fun getTooltipAction(info: HighlightInfo, editor: Editor): TooltipAction? {
    if (!Registry.`is`("ide.tooltip.show.with.actions")) return null

    return extractMostPriorityFix(info, editor)
  }
}

class DaemonTooltipAction(private val myFixText: String, private val myActualOffset: Int) : TooltipAction {

  override fun getText(): String {
    return myFixText
  }

  override fun execute(editor: Editor) {
    editor.caretModel.moveToOffset(myActualOffset)
    val project = editor.project ?: return
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val intentions = ShowIntentionsPass.getActionsToShow(editor, psiFile)
    val list = intentions.errorFixesToShow + intentions.inspectionFixesToShow + intentions.intentionsToShow
    for (descriptor in list) {
      val action = descriptor.action
      if (action.text == myFixText) {
        ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, myFixText)
        return
      }
    }
  }

  override fun showAllActions(editor: Editor) {
    editor.caretModel.moveToOffset(myActualOffset)
    val project = editor.project ?: return
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    ShowIntentionActionsHandler().invoke(project, editor, psiFile)
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    val info = o as DaemonTooltipAction?
    return myActualOffset == info!!.myActualOffset && myFixText == info.myFixText
  }

  override fun hashCode(): Int {
    return Objects.hash(myFixText, myActualOffset)
  }
}


fun extractMostPriorityFix(highlightInfo: HighlightInfo, editor: Editor): TooltipAction? {
  ApplicationManager.getApplication().assertReadAccessAllowed()

  val fixes = mutableListOf<HighlightInfo.IntentionActionDescriptor>()
  val quickFixActionMarkers = highlightInfo.quickFixActionRanges
  if (quickFixActionMarkers == null || quickFixActionMarkers.isEmpty()) return null

  fixes.addAll(quickFixActionMarkers.map { it.first }.toList())

  val project = editor.project ?: return null
  val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null


  val intentionsInfo = ShowIntentionsPass.IntentionsInfo()
  ShowIntentionsPass.fillIntentionsInfoForHighlightInfo(highlightInfo, intentionsInfo, fixes)
  intentionsInfo.filterActions(psiFile)

  val cachedIntentions = CachedIntentions.createAndUpdateActions(project, psiFile, editor, intentionsInfo)
  val allActions = cachedIntentions.allActions
  if (allActions.isEmpty()) return null
  allActions.forEach {
    var action = it.action
    if (action is IntentionActionDelegate) {
      action = action.delegate
    }

    if (action !is AbstractEmptyIntentionAction && action.isAvailable(project, editor, psiFile)) {
      val text = it.text
      //we cannot properly render html inside the fix button fixes with html text
      if (!XmlStringUtil.isWrappedInHtml(text)) {
        return DaemonTooltipAction(text, highlightInfo.actualStartOffset)
      }
    }
  }
  return null
}

