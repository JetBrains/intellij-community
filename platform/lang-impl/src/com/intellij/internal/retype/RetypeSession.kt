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
import com.intellij.ide.IdeEventQueue
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
import java.awt.event.KeyEvent
import java.io.File
import java.util.*

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

  private var skipLookupSuggestion = false
  private var textBeforeLookupSelection: String? = null

  // This stack will contain autocompletion elements
  // E.g. "}", "]", "*/", "* @return"
  val completionStack = ArrayDeque<String>()


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

    val processNextEvent = handleIdeaIntelligence()
    if (processNextEvent) return

    if (TemplateManager.getInstance(project).getActiveTemplate(editor) != null) {
      TemplateManager.getInstance(project).finishTemplate(editor)
      queueNextOrStop()
      return
    }

    val lookup = LookupManager.getActiveLookup(editor) as LookupImpl?
    if (lookup != null && !skipLookupSuggestion) {
      val currentLookupElement = lookup.currentItem
      if (currentLookupElement?.shouldAccept(lookup.lookupStart) == true) {
        lookup.focusDegree = LookupImpl.FocusDegree.FOCUSED
        scriptBuilder?.append("${ActionCommand.PREFIX} ${IdeActions.ACTION_CHOOSE_LOOKUP_ITEM}\n")
        typedRightBefore = false
        textBeforeLookupSelection = document.text
        executeEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)
        queueNextOrStop()
        return
      }
    }

    val c = originalText[pos++]
    typedChars++

    // Reset lookup related variables
    textBeforeLookupSelection = null
    if (c == ' ') skipLookupSuggestion = false // We expecting new lookup suggestions

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
        }
        else {
          it.append("%delayType $delayMillis|$c\n")
        }
      }
      IdeEventQueue.getInstance().postEvent(
        KeyEvent(editor.component, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c))
      typedRightBefore = true
    }
    queueNextOrStop()
  }

  /**
   * @return if next queue event should be processed
   */
  private fun handleIdeaIntelligence(): Boolean {
    if (document.text.take(pos) != originalText.take(pos)) {
      // Unexpected changes before current cursor position
      // (may be unwanted import)
      if (textBeforeLookupSelection != null) {
        // Unexpected changes was made by lookup.
        // Restore previous text state and set flag to skip further suggestions until whitespace will be typed
        WriteCommandAction.runWriteCommandAction(project) {
          document.replaceText(textBeforeLookupSelection ?: return@runWriteCommandAction, document.modificationStamp + 1)
        }
        skipLookupSuggestion = true
      }
      else {
        // There changes wasn't made by lookup, so we don't know how to handle them
        // Restore text as it should be at this point without any intelligence
        WriteCommandAction.runWriteCommandAction(project) {
          document.replaceText(originalText.take(pos) + originalText.takeLast(endPos), document.modificationStamp + 1)
        }
      }
    }

    if (editor.caretModel.offset > pos) {
      // Caret movement has been preformed
      // Move the caret forward until the characters match
      while (pos < document.textLength - tailLength
             && originalText[pos] == document.text[pos]
             && document.text[pos] !in listOf('\n') // Don't count line breakers because we want to enter "enter" explicitly
      ) {
        pos++
        completedChars++
      }
      if (editor.caretModel.offset > pos) {
        WriteCommandAction.runWriteCommandAction(project) {
          // Delete symbols not from original text and move caret
          document.deleteString(pos, editor.caretModel.offset)
        }
      }
      editor.caretModel.moveToOffset(pos)
    }

    if (document.textLength > pos + tailLength) {
      updateStack(completionStack)
      val firstCompletion = completionStack.peekLast()

      if (firstCompletion != null) {
        val origIndexOfFirstCompletion = originalText.substring(pos, endPos).trim().indexOf(firstCompletion)

        if (origIndexOfFirstCompletion == 0) {
          // Next non-whitespace chars from original tests are from complation stack
          val origIndexOfFirstComp = originalText.substring(pos, endPos).indexOf(firstCompletion)
          val docIndexOfFirstComp = document.text.substring(pos).indexOf(firstCompletion)
          if (originalText.substring(pos).take(origIndexOfFirstComp) != document.text.substring(pos).take(origIndexOfFirstComp)) {
            // We have some unexpected chars before completion. Remove them
            WriteCommandAction.runWriteCommandAction(project) {
              document.replaceString(pos, pos + docIndexOfFirstComp, originalText.substring(pos, pos + origIndexOfFirstComp))
            }
          }
          pos += origIndexOfFirstComp + firstCompletion.length
          editor.caretModel.moveToOffset(pos)
          completionStack.removeLast()
          queueNextOrStop()
          return true
        }
        else if (origIndexOfFirstCompletion < 0) {
          // Completion is wrong and original text doesn't contain it
          // Remove this completion
          val docIndexOfFirstComp = document.text.substring(pos).indexOf(firstCompletion)
          WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(pos, pos + docIndexOfFirstComp + firstCompletion.length, "")
          }
          completionStack.removeLast()
          queueNextOrStop()
          return true
        }
      }
    }
    else if (document.textLength == pos + tailLength && completionStack.isNotEmpty()) {
      // Text is as expected, but we have some extra completions in stack
      completionStack.clear()
    }
    return false
  }

  private fun updateStack(completionStack: Deque<String>) {
    val unexpectedCharsDoc = document.text.substring(pos, document.textLength - tailLength)

    var endPosDoc = unexpectedCharsDoc.length

    val completionIterator = completionStack.iterator()
    while (completionIterator.hasNext()) {
      // Validate all existing completions and add new completions if they are
      val completion = completionIterator.next()
      val lastIndexOfCompletion = unexpectedCharsDoc.substring(0, endPosDoc).lastIndexOf(completion)
      if (lastIndexOfCompletion < 0) {
        completionIterator.remove()
        continue
      }
      endPosDoc = lastIndexOfCompletion
    }

    // Add new completion in stack
    unexpectedCharsDoc.substring(0, endPosDoc).trim().split("\\s+".toRegex()).map { it.trim() }.reversed().forEach {
      if (it.isNotEmpty()) {
        completionStack.add(it)
      }
    }
  }

  private fun queueNextOrStop() {
    if (pos < endPos) {
      queueNext()
    }
    else {
      stop(true)

      if (startNextCallback == null && !ApplicationManager.getApplication().isUnitTestMode) {
        if (scriptBuilder != null) {
          scriptBuilder.append(correctText(originalText))
          val file = File.createTempFile("perf", ".test")
          val vFile = VfsUtil.findFileByIoFile(file, false)!!
          WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(vFile, scriptBuilder.toString())
          }
          OpenFileDescriptor(project, vFile).navigate(true)
        }
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
