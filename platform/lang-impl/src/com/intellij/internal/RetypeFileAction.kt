// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.DataManager
import com.intellij.internal.performance.TypingLatencyReportDialog
import com.intellij.internal.performance.latencyMap
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm

/**
 * @author yole
 */
class RetypeFileAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl ?: return
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val existingSession = editor.getUserData(RETYPE_SESSION_KEY)
    if (existingSession != null) {
      existingSession.stop()
    }
    else {
      val session = RetypeSession(project, editor, 400)
      editor.putUserData(RETYPE_SESSION_KEY, session)
      session.start()
    }
  }

  override fun update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    e.presentation.isEnabled = e.project != null && editor != null
    val retypeSession = editor?.getUserData(RETYPE_SESSION_KEY)
    if (retypeSession != null) {
      e.presentation.text = "Stop Retyping"
    }
    else {
      e.presentation.text = "Retype Current File"
    }
  }
}

class RetypeSession(private val project: Project, private val editor: EditorImpl, private val delayMillis: Int) : Disposable {
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val threadDumpAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val text = editor.document.text
  private var pos = 0
  private var completedChars = 0
  private val threadDumps = mutableListOf<String>()

  fun start() {
    latencyMap.clear()
    WriteCommandAction.runWriteCommandAction(project) { editor.document.deleteString(0, editor.document.textLength) }
    queueNext()
  }

  fun stop() {
    WriteCommandAction.runWriteCommandAction(project) { editor.document.replaceString(0, editor.document.textLength, text) }
    Disposer.dispose(this)
    editor.putUserData(RETYPE_SESSION_KEY, null)
  }

  override fun dispose() {
  }

  private fun queueNext() {
    alarm.addRequest(Runnable { typeNext() }, delayMillis)
  }

  private fun typeNext() {
    threadDumpAlarm.addRequest(Runnable { logThreadDump() }, 100)

    val lookup = LookupManager.getActiveLookup(editor)
    var lookupSelected = false
    if (lookup != null) {
      val lookupString = lookup.currentItem?.let { LookupElementPresentation.renderElement(it).itemText}
      if (lookupString != null && lookup.currentItem !is LiveTemplateLookupElement &&
          text.substring(lookup.lookupStart, lookup.lookupStart + lookupString.length) == lookupString) {
        executeEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)
        syncPositionWithEditor()
        lookupSelected = true
      }
    }
    if (!lookupSelected) {
      val c = text[pos]
      if (c == '\n') {
        // catch up with previously triggered typed handlers
        if (syncPositionWithEditor()) {
          queueNext()
          return
        }
        pos++
        executeEditorAction(IdeActions.ACTION_EDITOR_ENTER)
        syncPositionWithEditor()
      }
      else {
        pos++
        editor.type(c.toString())
      }
    }
    if (pos < text.length) {
      queueNext()
    }
    else {
      stop()
      if (editor.document.text != text) {
        Messages.showErrorDialog(project, "Retype failed, editor text differs", "Retype")
      }
      else {
        TypingLatencyReportDialog(project, threadDumps).show()
      }
    }
  }

  private fun syncPositionWithEditor(): Boolean {
    var result = false
    while (pos < editor.document.text.length && pos < text.length && editor.document.charsSequence[pos] == text[pos]) {
      result = true
      completedChars++
      pos++
    }
    if (editor.caretModel.offset <= pos) {
      editor.caretModel.moveToOffset(pos)
    }
    else {
      // unwanted completion, backtrack
      println("Text has diverged, backtracking. Editor text:\n${editor.document.text}\nBuffer text:\n${text.substring(0, pos)}")
      WriteCommandAction.runWriteCommandAction(project) {
        while (editor.caretModel.offset > pos) {
          executeEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE)
        }
      }
    }
    return result
  }

  private fun executeEditorAction(actionId: String) {
    val actionManager = ActionManagerEx.getInstanceEx()
    val action = actionManager.getAction(actionId)
    val event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContext(editor.component))
    action.beforeActionPerformedUpdate(event)
    actionManager.fireBeforeActionPerformed(action, event.dataContext, event)
    LatencyRecorder.getInstance().recordLatencyAwareAction(editor, actionId, System.currentTimeMillis())
    action.actionPerformed(event)
    actionManager.fireAfterActionPerformed(action, event.dataContext, event)
  }

  private fun logThreadDump() {
    if (editor.isProcessingTypedAction) {
      threadDumps.add(ThreadDumper.dumpThreadsToString())
      threadDumpAlarm.addRequest(Runnable { logThreadDump() }, 100)
    }
  }
}

val RETYPE_SESSION_KEY = Key.create<RetypeSession>("com.intellij.internal.RetypeSession")
