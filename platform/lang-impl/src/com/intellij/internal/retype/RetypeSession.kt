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
import com.intellij.internal.performance.latencyRecorderProperties
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
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors
import com.intellij.util.Alarm
import java.awt.event.KeyEvent
import java.io.File
import java.util.*
import kotlin.concurrent.timer

fun String.toReadable() = replace(" ", "<Space>").replace("\n", "<Enter>").replace("\t", "<Tab>")

class RetypeLog {
  private val log = arrayListOf<String>()
  private var currentTyping: String? = null
  private var currentCompletion: String? = null
  var typedChars = 0
    private set
  var completedChars = 0
    private set

  fun recordTyping(c: Char) {
    if (currentTyping == null) {
      flushCompletion()
      currentTyping = ""
    }
    currentTyping += c.toString().toReadable()
    typedChars++
  }

  fun recordCompletion(c: Char) {
    if (currentCompletion == null) {
      flushTyping()
      currentCompletion = ""
    }
    currentCompletion += c.toString().toReadable()
    completedChars++
  }

  fun recordDesync(message: String) {
    flush()
    log.add("Desync: $message")
  }

  fun flush() {
    flushTyping()
    flushCompletion()
  }

  private fun flushTyping() {
    if (currentTyping != null) {
      log.add("Type: $currentTyping")
      currentTyping = null
    }
  }

  private fun flushCompletion() {
    if (currentCompletion != null) {
      log.add("Complete: $currentCompletion")
      currentCompletion = null
    }
  }

  fun printToStdout() {
    for (s in log) {
      println(s)
    }
  }
}

/**
 * @property interfereFilesChangePeriod Set period in milliseconds for changes in interfere file.
 * "Interfere file" - file that will be created near by retyped and it will be periodically changed.
 * After retype session this file will be deleted.
 * Pass negative value to disable this functionality.
 */
class RetypeSession(
  private val project: Project,
  private val editor: EditorImpl,
  private val delayMillis: Int,
  private val scriptBuilder: StringBuilder?,
  private val threadDumpDelay: Int,
  private val threadDumps: MutableList<String> = mutableListOf(),
  private val interfereFilesChangePeriod: Long = -1,
  private val restoreText: Boolean = !ApplicationManager.getApplication().isUnitTestMode
) : Disposable {
  private val document = editor.document

  // -- Alarms
  private val threadDumpAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

  private val originalText = document.text
  private var pos = 0
  private val endPos: Int
  private val tailLength: Int

  private val log = RetypeLog()

  private val oldSelectAutopopup = CodeInsightSettings.getInstance().SELECT_AUTOPOPUP_SUGGESTIONS_BY_CHARS
  private val oldAddUnambiguous = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY
  private val oldOptimize = CodeInsightWorkspaceSettings.getInstance(project).optimizeImportsOnTheFly
  var startNextCallback: (() -> Unit)? = null
  private val disposeLock = Any()
  private var typedRightBefore = false

  private var skipLookupSuggestion = false
  private var textBeforeLookupSelection: String? = null
  @Volatile
  private var waitingForTimerInvokeLater: Boolean = false

  private var lastTimerTick = -1L
  private var threadPoolTimerLag = 0L
  private var totalTimerLag = 0L

  // This stack will contain autocompletion elements
  // E.g. "}", "]", "*/", "* @return"
  private val completionStack = ArrayDeque<String>()

  private var stopInterfereFileChanger = false
  val interfereFileName = "IdeaRetypeBackgroundChanges.java"

  var retypePaused: Boolean = false

  private val timerThread = Thread(::runLoop)
  private var stopTimer = false

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
    latencyRecorderProperties.putAll(mapOf("Delay" to "$delayMillis ms",
                                           "Thread dump delay" to "$threadDumpDelay ms",
                                           "Interfere file change period" to if (interfereFilesChangePeriod <= 0) "disabled" else "$interfereFilesChangePeriod ms"
    ))

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
    runInterfereFileChanger()
    EditorNotifications.getInstance(project).updateNotifications(editor.virtualFile)
    retypePaused = false
    timerThread.start()
    checkStop()
  }

  private fun correctText(text: String) = "%replaceText ${text.replace('\n', '\u32E1')}\n"

  fun stop(startNext: Boolean) {
    stopTimer = true
    for (retypeFileAssistant in RetypeFileAssistant.EP_NAME.extensions) {
      retypeFileAssistant.retypeDone(editor)
    }

    if (restoreText) {
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

    currentLatencyRecordKey?.details = "typed ${log.typedChars} chars, completed ${log.completedChars} chars"
    log.flush()
    log.printToStdout()
    currentLatencyRecordKey = null
    if (startNext) {
      startNextCallback?.invoke()
    }
    stopInterfereFileChanger = true
    EditorNotifications.getInstance(project).updateAllNotifications()
  }

  override fun dispose() {
  }

  private fun inFocus(): Boolean =
    editor.contentComponent == IdeFocusManager.findInstance().focusOwner && ApplicationManager.getApplication().isActive

  private fun runLoop() {
    while (true) {
      if (pos == endPos || stopTimer) break
      Thread.sleep(delayMillis.toLong())
      typeNext()
    }
  }

  private fun typeNext() {
    threadDumpAlarm.addRequest({ logThreadDump() }, threadDumpDelay)

    val timerTick = System.currentTimeMillis()
    waitingForTimerInvokeLater = true

    val expectedTimerTick = if (lastTimerTick != -1L) lastTimerTick + delayMillis else -1L
    if (lastTimerTick != -1L) {
      threadPoolTimerLag += (timerTick - expectedTimerTick)
    }
    lastTimerTick = timerTick
    ApplicationManager.getApplication().invokeLater {
      typeNextInEDT(timerTick, expectedTimerTick)
    }
  }

  private fun typeNextInEDT(timerTick: Long, expectedTimerTick: Long) {
    if (retypePaused) {
      if (inFocus()) {
        // Resume retyping on editor focus
        retypePaused = false
      }
      else {
        checkStop()
        return
      }
    }

    if (expectedTimerTick != -1L) {
      totalTimerLag += (System.currentTimeMillis() - expectedTimerTick)
    }

    EditorNotifications.getInstance(project).updateAllNotifications()
    waitingForTimerInvokeLater = false
    val processNextEvent = handleIdeaIntelligence()
    if (processNextEvent) return

    if (TemplateManager.getInstance(project).getActiveTemplate(editor) != null) {
      TemplateManager.getInstance(project).finishTemplate(editor)
      checkStop()
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
        executeEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM, timerTick)
        checkStop()
        return
      }
    }

    // Do not perform typing if editor is not in focus
    if (!inFocus()) retypePaused = true

    if (retypePaused) {
      checkStop()
      return
    }

    // Restore caret position (E.g. user clicks accidentally on another position)
    if (editor.caretModel.offset != pos) editor.caretModel.moveToOffset(pos)

    val c = originalText[pos++]
    log.recordTyping(c)

    // Reset lookup related variables
    textBeforeLookupSelection = null
    if (c == ' ') skipLookupSuggestion = false // We expecting new lookup suggestions

    if (c == '\n') {
      scriptBuilder?.append("${ActionCommand.PREFIX} ${IdeActions.ACTION_EDITOR_ENTER}\n")
      executeEditorAction(IdeActions.ACTION_EDITOR_ENTER, timerTick)
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
        KeyEvent(editor.component, KeyEvent.KEY_PRESSED, timerTick, 0, KeyEvent.VK_UNDEFINED, c))
      IdeEventQueue.getInstance().postEvent(
        KeyEvent(editor.component, KeyEvent.KEY_TYPED, timerTick, 0, KeyEvent.VK_UNDEFINED, c))
      typedRightBefore = true
    }
    checkStop()
  }

  /**
   * @return if next queue event should be processed
   */
  private fun handleIdeaIntelligence(): Boolean {
    val actualBeforeCaret = document.text.take(pos)
    val expectedBeforeCaret = originalText.take(pos)
    if (actualBeforeCaret != expectedBeforeCaret) {
      // Unexpected changes before current cursor position
      // (may be unwanted import)
      if (textBeforeLookupSelection != null) {
        log.recordDesync("Restoring text before lookup (expected ...${expectedBeforeCaret.takeLast(
          5).toReadable()}, actual ...${actualBeforeCaret.takeLast(5).toReadable()} ")
        // Unexpected changes was made by lookup.
        // Restore previous text state and set flag to skip further suggestions until whitespace will be typed
        WriteCommandAction.runWriteCommandAction(project) {
          document.replaceText(textBeforeLookupSelection ?: return@runWriteCommandAction, document.modificationStamp + 1)
        }
        skipLookupSuggestion = true
      }
      else {
        log.recordDesync(
          "Restoring entire text (expected ...${expectedBeforeCaret.takeLast(5).toReadable()}, actual ...${actualBeforeCaret.takeLast(
            5).toReadable()} ")
        // There changes wasn't made by lookup, so we don't know how to handle them
        // Restore text as it should be at this point without any intelligence
        WriteCommandAction.runWriteCommandAction(project) {
          document.replaceText(expectedBeforeCaret + originalText.takeLast(originalText.length - endPos), document.modificationStamp + 1)
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
        log.recordCompletion(document.text[pos])
        pos++
      }
      if (editor.caretModel.offset > pos) {
        log.recordDesync("Deleting extra characters: ${document.text.substring(pos, editor.caretModel.offset).toReadable()}")
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
          // Next non-whitespace chars from original tests are from completion stack
          val origIndexOfFirstComp = originalText.substring(pos, endPos).indexOf(firstCompletion)
          val docIndexOfFirstComp = document.text.substring(pos).indexOf(firstCompletion)
          if (originalText.substring(pos).take(origIndexOfFirstComp) != document.text.substring(pos).take(origIndexOfFirstComp)) {
            // We have some unexpected chars before completion. Remove them
            WriteCommandAction.runWriteCommandAction(project) {
              val replacement = originalText.substring(pos, pos + origIndexOfFirstComp)
              log.recordDesync("Replacing extra characters before completion: ${document.text.substring(pos,
                                                                                                        pos + docIndexOfFirstComp).toReadable()} -> ${replacement.toReadable()}")
              document.replaceString(pos, pos + docIndexOfFirstComp, replacement)
            }
          }
          (pos until pos + origIndexOfFirstComp + firstCompletion.length).forEach { log.recordCompletion(document.text[it]) }
          pos += origIndexOfFirstComp + firstCompletion.length
          editor.caretModel.moveToOffset(pos)
          completionStack.removeLast()
          checkStop()
          return true
        }
        else if (origIndexOfFirstCompletion < 0) {
          // Completion is wrong and original text doesn't contain it
          // Remove this completion
          val docIndexOfFirstComp = document.text.substring(pos).indexOf(firstCompletion)
          log.recordDesync("Removing wrong completion: ${document.text.substring(pos, pos + docIndexOfFirstComp).toReadable()}")
          WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(pos, pos + docIndexOfFirstComp + firstCompletion.length, "")
          }
          completionStack.removeLast()
          checkStop()
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

  private fun checkStop() {
    if (pos == endPos) {
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
        latencyRecorderProperties["Thread pool timer lag"] = "$threadPoolTimerLag ms"
        latencyRecorderProperties["Total timer lag"] = "$totalTimerLag ms"
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

  private fun executeEditorAction(actionId: String, timerTick: Long) {
    val actionManager = ActionManagerEx.getInstanceEx()
    val action = actionManager.getAction(actionId)
    val event = AnActionEvent.createFromAnAction(action, null, "",
                                                 DataManager.getInstance().getDataContext(
                                                   editor.component))
    action.beforeActionPerformedUpdate(event)
    actionManager.fireBeforeActionPerformed(action, event.dataContext, event)
    LatencyRecorder.getInstance().recordLatencyAwareAction(editor, actionId, timerTick)
    action.actionPerformed(event)
    actionManager.fireAfterActionPerformed(action, event.dataContext, event)
  }

  private fun logThreadDump() {
    if (editor.isProcessingTypedAction || waitingForTimerInvokeLater) {
      threadDumps.add(ThreadDumper.dumpThreadsToString())
      if (threadDumps.size > 200) {
        threadDumps.subList(0, 100).clear()
      }
      synchronized(disposeLock) {
        if (!threadDumpAlarm.isDisposed) {
          threadDumpAlarm.addRequest({ logThreadDump() }, threadDumpDelay)
        }
      }
    }
  }

  private fun runInterfereFileChanger() {
    if (interfereFilesChangePeriod <= 0) return
    stopInterfereFileChanger = false

    val file = File(editor.virtualFile.parent.path, interfereFileName)
    file.createNewFile()

    val text = "// Text\n".repeat(500)
    file.writeText(text)

    // Increment this counter to make vision that something really changes.
    var counter = 0
    timer(period = interfereFilesChangePeriod) {
      counter++
      file.writeText("$text  Additional ${counter}")
      if (stopInterfereFileChanger) {
        file.delete()
        cancel()
      }
    }
  }

  companion object {
    val LOG = Logger.getInstance("#com.intellij.internal.retype.RetypeSession")
  }
}

val RETYPE_SESSION_KEY = Key.create<RetypeSession>("com.intellij.internal.retype.RetypeSession")
val RETYPE_SESSION_NOTIFICATION_KEY = Key.create<EditorNotificationPanel>("com.intellij.internal.retype.RetypeSessionNotification")


class RetypeEditorNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  override fun getKey(): Key<EditorNotificationPanel> = RETYPE_SESSION_NOTIFICATION_KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel? {
    if (fileEditor !is PsiAwareTextEditorImpl) return null

    val retypeSession = fileEditor.editor.getUserData(RETYPE_SESSION_KEY)
    if (retypeSession == null) return null

    val panel: EditorNotificationPanel

    if (retypeSession.retypePaused) {
      panel = EditorNotificationPanel()
      panel.setText("Pause retyping. Click on editor to resume")
    }
    else {
      panel = EditorNotificationPanel(LightColors.SLIGHTLY_GREEN)
      panel.setText("Retyping")
    }
    panel.createActionLabel("Stop without report") {
      retypeSession.stop(false)
    }
    return panel
  }
}
