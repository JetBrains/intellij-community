// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.tooltips.TooltipActionProvider
import com.intellij.codeInsight.intention.AbstractEmptyIntentionAction
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.internal.statistic.service.fus.collectors.TooltipActionsLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.TooltipAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.xml.util.XmlStringUtil
import java.awt.event.InputEvent
import java.util.*

class DaemonTooltipActionProvider : TooltipActionProvider {
  override fun getTooltipAction(info: HighlightInfo, editor: Editor, psiFile: PsiFile): TooltipAction? {
    val intention = extractMostPriorityFixFromHighlightInfo(info, editor, psiFile) ?: return null
    return wrapIntentionToTooltipAction(intention, info, editor)
  }

}

/**
 * Tooltip link-action that proxies its execution to intention action with text [myActionText]
 * @param myFixText is a text to show in tooltip
 * @param myActionText is a text to search for in intentions' actions
 */
class DaemonTooltipAction(@NlsActions.ActionText private val myFixText: String, @NlsContexts.Command private val myActionText: String, private val myActualOffset: Int) : TooltipAction {

  override fun getText(): String {
    return myFixText
  }

  override fun execute(editor: Editor, inputEvent: InputEvent?) {
    val project = editor.project ?: return

    TooltipActionsLogger.logExecute(project, inputEvent)
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val intentions = ShowIntentionsPass.getAvailableFixes(editor, psiFile, -1, myActualOffset)

    for (descriptor in intentions) {
      val action = descriptor.action
      if (action.text == myActionText) {
        //unfortunately it is very common case when quick fixes/refactorings use caret position
        editor.caretModel.moveToOffset(myActualOffset)
        ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, action, myActionText)
        return
      }
    }
  }

  override fun showAllActions(editor: Editor) {
    editor.caretModel.moveToOffset(myActualOffset)
    val project = editor.project ?: return

    TooltipActionsLogger.showAllEvent.log(project)
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
    val action = IntentionActionDelegate.unwrap(it.action)

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

fun wrapIntentionToTooltipAction(intention: IntentionAction,
                                 info: HighlightInfo,
                                 editor: Editor): TooltipAction {
  val editorOffset = editor.caretModel.offset
  val text = (intention as? CustomizableIntentionAction)?.tooltipText ?: intention.text

  if ((info.actualStartOffset .. info.actualEndOffset).contains(editorOffset)) {
    //try to avoid caret movements
    return DaemonTooltipAction(text, intention.text, editorOffset)
  }
  val pair = info.quickFixActionMarkers?.find { it.first?.action == intention }
  val offset = if (pair?.second?.isValid == true) pair.second.startOffset else info.actualStartOffset
  return DaemonTooltipAction(text, intention.text, offset)
}
