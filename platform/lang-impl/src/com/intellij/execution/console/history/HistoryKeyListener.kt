// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.console.history

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class HistoryKeyListener(
  private val project: Project,
  private val consoleEditor: EditorEx,
  private val history: CommandHistory,
  val moveCaretToEndOnUp: Boolean = false,
  val keyExactMatch : Boolean = false) :
    KeyAdapter(), HistoryUpdateListener {
    private var historyPos = 0
    private var prevCaretOffset = -1
    private var unfinishedCommand = ""

    override fun onNewEntry(entry: CommandHistory.Entry) {
        // reset history positions
        historyPos = history.size
        prevCaretOffset = -1
        unfinishedCommand = ""
    }

    private enum class HistoryMove {
        UP, DOWN
    }

    override fun keyReleased(e: KeyEvent) {
        if (keyExactMatch && e.modifiersEx != 0) return //suppress navigation if there are additional keys being pressed
        when (e.keyCode) {
            KeyEvent.VK_UP -> moveHistoryCursor(HistoryMove.UP)
            KeyEvent.VK_DOWN -> moveHistoryCursor(HistoryMove.DOWN)
            KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT -> prevCaretOffset = consoleEditor.caretModel.offset
        }
    }

    private fun moveHistoryCursor(move: HistoryMove) {
        if (history.size == 0) return
        if (LookupManager.getInstance(project).activeLookup != null) return

        val caret = consoleEditor.caretModel
        val document = consoleEditor.document

        val curOffset = caret.offset
        val curLine = document.getLineNumber(curOffset)
        val totalLines = document.lineCount
        val isMultiline = totalLines > 1

        when (move) {
            HistoryMove.UP -> {
                if (curLine != 0 || (isMultiline && prevCaretOffset != 0)) {
                    prevCaretOffset = curOffset
                    return
                }

                if (historyPos == history.size) {
                    unfinishedCommand = document.text
                }

                val isTop = historyPos == 0
                historyPos = max(historyPos - 1, 0)
                WriteCommandAction.runWriteCommandAction(project) {
                    val text = history[historyPos].entryText
                    document.setText(text)
                    EditorUtil.scrollToTheEnd(consoleEditor)

                    if (moveCaretToEndOnUp && !isTop) {
                      prevCaretOffset = text.length
                      caret.moveToOffset(text.length)
                      return@runWriteCommandAction
                    }

                    prevCaretOffset = 0
                    caret.moveToOffset(0)
                }
            }
            HistoryMove.DOWN -> {
                if (historyPos == history.size) return

                if (curLine != totalLines - 1 || (isMultiline && prevCaretOffset != document.textLength)) {
                    prevCaretOffset = curOffset
                    return
                }

                historyPos = min(historyPos + 1, history.size)
                WriteCommandAction.runWriteCommandAction(project) {
                    document.setText(if (historyPos == history.size) unfinishedCommand else history[historyPos].entryText)
                    prevCaretOffset = document.textLength
                    EditorUtil.scrollToTheEnd(consoleEditor)
                }
            }
        }
    }
}