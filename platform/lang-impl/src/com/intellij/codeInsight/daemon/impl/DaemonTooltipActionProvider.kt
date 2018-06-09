// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.tooltips.TooltipActionProvider
import com.intellij.codeInsight.intention.AbstractEmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.ide.actions.ActionsCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.TooltipAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.xml.util.XmlStringUtil
import java.util.*

class DaemonTooltipActionProvider : TooltipActionProvider {
  override fun getTooltipAction(info: HighlightInfo, editor: Editor, psiFile: PsiFile): TooltipAction? {
    val intention = extractMostPriorityFixFromHighlightInfo(info, editor, psiFile) ?: return null
    return wrapIntentionToTooltipAction(intention, info)
  }

}

class DaemonTooltipAction(private val myFixText: String, private val myActualOffset: Int) : TooltipAction {

  override fun getText(): String {
    return myFixText
  }

  override fun execute(editor: Editor) {
    ActionsCollector.getInstance().record("tooltip.actions.execute")

    val project = editor.project ?: return
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val intentions = ShowIntentionsPass.getActionsToShow(editor, psiFile, myActualOffset)
    val list = intentions.errorFixesToShow + intentions.inspectionFixesToShow + intentions.intentionsToShow
    for (descriptor in list) {
      val action = descriptor.action
      if (action.text == myFixText) {
        if (action !is QuickFixWrapper) {
          //unfortunately it is very common case when q fix uses caret position :(
          editor.caretModel.moveToOffset(myActualOffset)
        }
        ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, myFixText)
        return
      }
    }
  }

  override fun showAllActions(editor: Editor) {
    ActionsCollector.getInstance().record("tooltip.actions.show.all")

    editor.caretModel.moveToOffset(myActualOffset)
    val project = editor.project ?: return
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    ShowIntentionActionsHandler().invoke(project, editor, psiFile)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val info = other as DaemonTooltipAction?
    return myActualOffset == info!!.myActualOffset && myFixText == info.myFixText
  }

  override fun hashCode(): Int {
    return Objects.hash(myFixText, myActualOffset)
  }
}


fun extractMostPriorityFixFromHighlightInfo(highlightInfo: HighlightInfo, editor: Editor, psiFile: PsiFile): IntentionAction? {
  ApplicationManager.getApplication().assertReadAccessAllowed()

  val fixes = mutableListOf<HighlightInfo.IntentionActionDescriptor>()
  val quickFixActionMarkers = highlightInfo.quickFixActionRanges
  if (quickFixActionMarkers == null || quickFixActionMarkers.isEmpty()) return null

  fixes.addAll(quickFixActionMarkers.map { it.first }.toList())

  val intentionsInfo = ShowIntentionsPass.IntentionsInfo()
  ShowIntentionsPass.fillIntentionsInfoForHighlightInfo(highlightInfo, intentionsInfo, fixes)
  intentionsInfo.filterActions(psiFile)

  return getFirstAvailableAction(psiFile, editor, intentionsInfo)
}

fun getFirstAvailableAction(psiFile: PsiFile,
                            editor: Editor,
                            intentionsInfo: ShowIntentionsPass.IntentionsInfo): IntentionAction? {
  val project = psiFile.project
  
  //sort the actions
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
        return action
      }
    }
  }
  return null
}

fun wrapIntentionToTooltipAction(intention: IntentionAction, info: HighlightInfo) =
  DaemonTooltipAction(intention.text, info.actualStartOffset)



