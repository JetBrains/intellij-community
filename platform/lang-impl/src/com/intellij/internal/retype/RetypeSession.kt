// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElement
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.DataManager
import com.intellij.internal.performance.LatencyDistributionRecordKey
import com.intellij.internal.performance.TypingLatencyReportDialog
import com.intellij.internal.performance.currentLatencyRecordKey
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.Alarm

class RetypeSession(
  private val project: Project,
  private val editor: EditorImpl,
  private val delayMillis: Int,
  private val threadDumpDelay: Int,
  private val threadDumps: MutableList<String> = mutableListOf()
) : Disposable {
  private val document = editor.document
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val threadDumpAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val originalText = editor.document.text
  private val lines = editor.document.text.split('\n').map { it + "\n" }
  private var line = -1
  private var column = 0
  private var typedChars = 0
  private var completedChars = 0
  private var backtrackedChars = 0
  private val oldSelectAutopopup = CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS
  private val oldAddUnambiguous = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
  private var needSyncPosition = false
  private var editorLineBeforeAcceptingLookup = -1
  var startNextCallback: (() -> Unit)? = null
  private val disposeLock = Any()

  val currentLineText get() = lines[line]

  fun start() {
    editor.putUserData(RETYPE_SESSION_KEY, this)
    val vFile = FileDocumentManager.getInstance().getFile(document)
    val keyName = "${vFile?.name ?: "Unknown file"} (${document.textLength} chars)"
    currentLatencyRecordKey = LatencyDistributionRecordKey(keyName)
    line = editor.caretModel.logicalPosition.line - 1
    val currentLineStart = document.getLineStartOffset(line + 1)
    WriteCommandAction.runWriteCommandAction(project) { document.deleteString(currentLineStart, document.textLength) }
    CodeInsightSettings.getInstance().apply {
      SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
      ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false
    }
    queueNext()
  }

  fun stop(startNext: Boolean) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      WriteCommandAction.runWriteCommandAction(project) { document.replaceString(0, document.textLength, originalText) }
    }
    synchronized(disposeLock) {
      Disposer.dispose(this)
    }
    editor.putUserData(RETYPE_SESSION_KEY, null)
    CodeInsightSettings.getInstance().apply {
      SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = oldSelectAutopopup
      ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = oldAddUnambiguous
    }
    currentLatencyRecordKey?.details = "typed $typedChars chars, completed $completedChars chars, backtracked $backtrackedChars chars"
    currentLatencyRecordKey = null
    if (startNext) {
      startNextCallback?.invoke()
    }
  }

  override fun dispose() {
  }

  private fun queueNext() {
    if (!alarm.isDisposed) {
      alarm.addRequest({ typeNext() }, delayMillis)
    }
  }

  private fun typeNext() {
    threadDumpAlarm.addRequest({ logThreadDump() }, threadDumpDelay)

    if (column == 0) {
      if (line >= 0) {
        if (checkPrevLineInSync()) return
      }
      line++
      syncPositionWithEditor()
    }
    else if (needSyncPosition) {
      val lineDelta = editor.caretModel.logicalPosition.line - editorLineBeforeAcceptingLookup
      if (lineDelta > 0) {
        if (lineDelta == 1) {
          checkPrevLineInSync()
        }
        line += lineDelta
        column = 0
      }
      syncPositionWithEditor()
    }

    if (TemplateManager.getInstance(project).getActiveTemplate(editor) != null) {
      TemplateManager.getInstance(project).finishTemplate(editor)
      queueNextOrStop(true)
      return
    }

    val lookup = LookupManager.getActiveLookup(editor) as LookupImpl?
    if (lookup != null) {
      val currentLookupElement = lookup.currentItem
      if (currentLookupElement?.shouldAccept(lookup.lookupStart) == true) {
        lookup.focusDegree = LookupImpl.FocusDegree.FOCUSED
        editorLineBeforeAcceptingLookup = editor.caretModel.logicalPosition.line
        executeEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)
        queueNextOrStop(true)
        return
      }
    }

    val c = currentLineText[column]
    typedChars++
    if (c == '\n') {
      column = 0   // line will be incremented in next loop

      // Check if the next line was partially inserted with some insert handler (e.g. braces in java)
      if (line + 1 < document.lineCount
          && line + 1 < lines.size
          && lines[line + 1].startsWith(getEditorLineText(line + 1))) {
        // the caret will be moved right during the next position sync
        executeEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
      }
      else {
        executeEditorAction(IdeActions.ACTION_EDITOR_ENTER)
      }
    }
    else {
      column++
      editor.type(c.toString())
    }
    queueNextOrStop(false)
  }

  private fun queueNextOrStop(needSyncPosition: Boolean) {
    this.needSyncPosition = needSyncPosition
    if ((column == 0 && line < lines.size - 1) || (column > 0 && column < currentLineText.length)) {
      queueNext()
    }
    else {
      stop(true)

      if (startNextCallback == null && !ApplicationManager.getApplication().isUnitTestMode) {
        TypingLatencyReportDialog(project, threadDumps).show()
      }
    }
  }

  private fun LookupElement.shouldAccept(lookupStartOffset: Int): Boolean {
    for (retypeFileAssistant in Extensions.getExtensions(
      RetypeFileAssistant.EP_NAME)) {
      if (!retypeFileAssistant.acceptLookupElement(this)) {
        return false
      }
    }
    if (this is LiveTemplateLookupElement) {
      return false
    }
    val lookupString = LookupElementPresentation.renderElement(this).itemText ?: return false
    val lookupStartColumn = editor.offsetToLogicalPosition(lookupStartOffset).column
    val textAtColumn = currentLineText.drop(lookupStartColumn)
    if (textAtColumn.take(lookupString.length) != lookupString) {
      return false
    }
    return textAtColumn.length == lookupString.length ||
           !Character.isJavaIdentifierPart(textAtColumn[lookupString.length] + 1)
  }

  private fun checkPrevLineInSync(): Boolean {
    val prevLine = getEditorLineText(editor.caretModel.logicalPosition.line - 1)
    if (prevLine.trimEnd() != currentLineText.trimEnd()) {
      stop(false)
      Messages.showErrorDialog(project, "Text has diverged. Expected:\n$currentLineText\nActual:\n$prevLine",
                               "Retype File")
      return true
    }
    return false
  }

  private fun getEditorLineText(line: Int): String {
    val prevLineStart = document.getLineStartOffset(line)
    val prevLineEnd = document.getLineEndOffset(line)
    return document.text.substring(prevLineStart, prevLineEnd)
  }

  private fun syncPositionWithEditor(): Boolean {
    var result = false
    val editorLine = editor.caretModel.logicalPosition.line
    val editorLineText = getEditorLineText(editorLine)
    while (column < editorLineText.length && column < currentLineText.length && editorLineText[column] == currentLineText[column]) {
      result = true
      completedChars++
      column++
    }
    if (editor.caretModel.logicalPosition.column < column) {
      editor.caretModel.moveToLogicalPosition(LogicalPosition(line, column))
    }
    else if (editor.caretModel.logicalPosition.column > column) {
      // unwanted completion, backtrack
      println("Text has diverged, backtracking. Editor text:\n$editorLineText\nBuffer text:\n$currentLineText")
      val startOffset = document.getLineStartOffset(editorLine) + column
      val endOffset = document.getLineEndOffset(editorLine)
      backtrackedChars += endOffset - startOffset
      WriteCommandAction.runWriteCommandAction(project) {
        editor.document.deleteString(startOffset, endOffset)
      }
    }
    return result
  }

  private fun executeEditorAction(actionId: String) {
    val actionManager = ActionManagerEx.getInstanceEx()
    val action = actionManager.getAction(actionId)
    val event = AnActionEvent.createFromAnAction(action, null, "",
                                                 DataManager.getInstance().getDataContext(
                                                                                     editor.component))
    action.beforeActionPerformedUpdate(event)
    actionManager.fireBeforeActionPerformed(action, event.dataContext, event)
    LatencyRecorder.getInstance().recordLatencyAwareAction(editor, actionId, System.currentTimeMillis())
    action.actionPerformed(event)
    actionManager.fireAfterActionPerformed(action, event.dataContext, event)
  }

  private fun logThreadDump() {
    if (editor.isProcessingTypedAction) {
      threadDumps.add(ThreadDumper.dumpThreadsToString())
      synchronized(disposeLock) {
        if (!threadDumpAlarm.isDisposed) {
          threadDumpAlarm.addRequest({ logThreadDump() }, 100)
        }
      }
    }
  }

  companion object {
    val LOG = Logger.getInstance("#com.intellij.internal.retype.RetypeSession")
  }
}

val RETYPE_SESSION_KEY = Key.create<RetypeSession>("com.intellij.internal.retype.RetypeSession")
