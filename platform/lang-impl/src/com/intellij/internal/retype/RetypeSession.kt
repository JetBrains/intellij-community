// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.CodeInsightWorkspaceSettings
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
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.Alarm
import java.io.File

class RetypeSession(
  private val project: Project,
  private val editor: EditorImpl,
  private val delayMillis: Int,
  private val scriptBuilder: StringBuilder?,
  private val threadDumpDelay: Int,
  private val threadDumps: MutableList<String> = mutableListOf()
) : Disposable {
  private val document = editor.document
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val threadDumpAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val originalText = document.text
  private var pos = 0
  private val endPos: Int
  private val tailLength: Int
  private var typedChars = 0
  private var completedChars = 0
  private val oldSelectAutopopup = CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS
  private val oldAddUnambiguous = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
  private val oldOptimize = CodeInsightWorkspaceSettings.getInstance(project).optimizeImportsOnTheFly
  var startNextCallback: (() -> Unit)? = null
  private val disposeLock = Any()
  private var typedRightBefore = false

  init {
    if (editor.selectionModel.hasSelection()) {
      pos = editor.selectionModel.selectionStart
      endPos = editor.selectionModel.selectionEnd
    }
    else {
      pos = editor.caretModel.offset
      endPos = document.textLength
    }
    tailLength = document.textLength - endPos
  }

  fun start() {
    editor.putUserData(RETYPE_SESSION_KEY, this)
    val vFile = FileDocumentManager.getInstance().getFile(document)
    val keyName = "${vFile?.name ?: "Unknown file"} (${document.textLength} chars)"
    currentLatencyRecordKey = LatencyDistributionRecordKey(keyName)
    scriptBuilder?.let {
      if (vFile != null) {
        val contentRoot = ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(vFile) ?: return@let
        it.append("%openFile ${VfsUtil.getRelativePath(vFile, contentRoot)}\n")
      }
      it.append(correctText(originalText.substring(0, pos) + originalText.substring(endPos)))
      val line = editor.document.getLineNumber(pos)
      it.append("%goto ${line + 1} ${pos - editor.document.getLineStartOffset(line) + 1}\n")
    }
    WriteCommandAction.runWriteCommandAction(project) { document.deleteString(pos, endPos) }
    CodeInsightSettings.getInstance().apply {
      SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS = false
      ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = false
    }
    CodeInsightWorkspaceSettings.getInstance(project).optimizeImportsOnTheFly = false
    queueNextOrStop()
  }

  private fun correctText(text: String) = "%replaceText ${text.replace('\n', '\u32E1')}\n"

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
    CodeInsightWorkspaceSettings.getInstance(project).optimizeImportsOnTheFly = oldOptimize

    currentLatencyRecordKey?.details = "typed $typedChars chars, completed $completedChars chars"
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

    var expectedText = originalText.substring(0, pos) + originalText.substring(endPos)
    if (document.text != expectedText) {
      if (document.textLength >= pos && document.text.substring(0, pos) == originalText.substring(0, pos)) {
        while (pos + 1 < document.textLength - tailLength && originalText[pos] == document.text[pos]) {
          pos++
          completedChars++
        }
        expectedText = originalText.substring(0, pos) + originalText.substring(endPos)
      }

      if (document.text != expectedText) {
        typedRightBefore = false
        scriptBuilder?.append(correctText(expectedText))
        WriteCommandAction.runWriteCommandAction(project) {
          document.replaceText(expectedText, document.modificationStamp + 1)
        }
      }
      editor.caretModel.moveToOffset(pos)
    }

    if (TemplateManager.getInstance(project).getActiveTemplate(editor) != null) {
      TemplateManager.getInstance(project).finishTemplate(editor)
      queueNextOrStop()
      return
    }

    val lookup = LookupManager.getActiveLookup(editor) as LookupImpl?
    if (lookup != null) {
      val currentLookupElement = lookup.currentItem
      if (currentLookupElement?.shouldAccept(lookup.lookupStart) == true) {
        lookup.focusDegree = LookupImpl.FocusDegree.FOCUSED
        scriptBuilder?.append("${ActionCommand.PREFIX} ${IdeActions.ACTION_CHOOSE_LOOKUP_ITEM}\n")
        typedRightBefore = false
        executeEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)
        queueNextOrStop()
        return
      }
    }

    val c = originalText[pos++]
    typedChars++
    if (c == '\n') {
      scriptBuilder?.append("${ActionCommand.PREFIX} ${IdeActions.ACTION_EDITOR_ENTER}\n")
      executeEditorAction(IdeActions.ACTION_EDITOR_ENTER)
      typedRightBefore = false
    }
    else {
      scriptBuilder?.let {
        if (typedRightBefore) {
          it.deleteCharAt(it.length - 1)
          it.append("$c\n")
        } else {
          it.append("%delayType $delayMillis|$c\n")
        }
      }
      editor.type(c.toString())
      typedRightBefore = true
    }
    queueNextOrStop()
  }

  private fun queueNextOrStop() {
    if (pos < endPos) {
      queueNext()
    }
    else {
      stop(true)

      if (startNextCallback == null && !ApplicationManager.getApplication().isUnitTestMode) {
        scriptBuilder?.append(correctText(originalText))
        val file = File.createTempFile("perf", ".test")
        val vFile = VfsUtil.findFileByIoFile(file, false)!!
        VfsUtil.saveText(vFile, scriptBuilder.toString())
        OpenFileDescriptor(project, vFile).navigate(true)
        TypingLatencyReportDialog(project, threadDumps).show()
      }
    }
  }

  private fun LookupElement.shouldAccept(lookupStartOffset: Int): Boolean {
    for (retypeFileAssistant in RetypeFileAssistant.EP_NAME.extensionList) {
      if (!retypeFileAssistant.acceptLookupElement(this)) {
        return false
      }
    }
    if (this is LiveTemplateLookupElement) {
      return false
    }
    val lookupString = try {
      LookupElementPresentation.renderElement(this).itemText ?: return false
    }
    catch (e: Exception) {
      return false
    }
    val textAtLookup = originalText.substring(lookupStartOffset)
    if (textAtLookup.take(lookupString.length) != lookupString) {
      return false
    }
    return textAtLookup.length == lookupString.length ||
           !Character.isJavaIdentifierPart(textAtLookup[lookupString.length] + 1)
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
      if (threadDumps.size > 200) {
        threadDumps.subList(0, 100).clear()
      }
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
