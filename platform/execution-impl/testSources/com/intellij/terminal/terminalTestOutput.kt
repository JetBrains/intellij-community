// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.util.asSafely
import com.jediterm.terminal.Terminal
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.CharBuffer
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalModelListener
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import com.pty4j.windows.conpty.WinConPtyProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun TerminalExecutionConsole.awaitOutputEndsWithLines(
  timeout: Duration = DEFAULT_OUTPUT_TIMEOUT,
  expectedEndLines: List<String>,
) {
  awaitTextBufferCondition(timeout, terminalWidget.terminalTextBuffer) {
    val output = TerminalOutput.collect(terminalWidget)
    output.assertEndsWithLines(expectedEndLines)
  }
}

internal fun TerminalExecutionConsole.assertOutputStartsWithLines(expectedStartLines: List<String>) {
  val output = TerminalOutput.collect(terminalWidget)
  output.assertStartsWithLines(expectedStartLines)
}

internal suspend fun TerminalExecutionConsole.awaitOutputContainsSubstring(
  timeout: Duration = DEFAULT_OUTPUT_TIMEOUT,
  substringToFind: String,
) {
  awaitTextBufferCondition(timeout, terminalWidget.terminalTextBuffer) {
    val output = TerminalOutput.collect(terminalWidget)
    output.assertContainsSubstring(substringToFind)
  }
}

internal fun TerminalOutput.assertContainsSubstring(substringToFind: String) {
  if (!contains(substringToFind)) {
    Assertions.fail<Unit>("Cannot find '$substringToFind' in the output: ${getTextLines()}")
  }
}

internal fun TerminalOutput.assertContainsChunk(chunkToFind: TerminalOutputChunk) {
  if (!this.contains(chunkToFind)) {
    Assertions.fail<Unit>("Cannot find '$chunkToFind' in the output: ${getTextLines()}")
  }
}

internal fun TerminalOutput.assertLinesAre(expectedLines: List<String>) {
  val actualLines = getTextLines()
  Assertions.assertThat(actualLines).isEqualTo(expectedLines)
}

private suspend fun awaitTextBufferCondition(
  timeout: Duration,
  textBuffer: TerminalTextBuffer,
  assertCondition: () -> Unit,
) {
  val conditionHolds: () -> Boolean = {
    runCatching { assertCondition() }.isSuccess
  }
  val conditionDeferred = CompletableDeferred<Unit>()
  val listener = TerminalModelListener {
    if (conditionHolds()) {
      conditionDeferred.complete(Unit)
    }
  }
  textBuffer.addModelListener(listener)
  try {
    if (!conditionHolds()) {
      withTimeout(timeout) {
        conditionDeferred.await()
      }
      assertCondition()
    }
  }
  catch (e: TimeoutCancellationException) {
    System.err.println(e.message)
    assertCondition()
    Assertions.fail(e)
  }
  finally {
    textBuffer.removeModelListener(listener)
  }
}

internal class TerminalOutput(val lines: List<TerminalOutputLine>) {

  fun contains(substringToFind: String): Boolean = lines.any { it.contains(substringToFind) }

  fun contains(chunkToFind: TerminalOutputChunk): Boolean = lines.any {
    it.contains(chunkToFind)
  }

  fun getTextLines(): List<String> {
    return lines.map { it.lineText }
  }

  companion object {
    fun collect(terminalWidget: JBTerminalWidget): TerminalOutput {
      val trimLineEnds = terminalWidget.ttyConnector.asSafely<ProcessHandlerTtyConnector>()?.let {
        it.process is WinConPtyProcess
      } ?: false
      return collect(terminalWidget.terminalTextBuffer, terminalWidget.terminal, trimLineEnds)
    }

    private fun collect(textBuffer: TerminalTextBuffer, terminal: Terminal, trimLineEnds: Boolean): TerminalOutput {
      return TerminalOutputBuilder(textBuffer, terminal, trimLineEnds).build()
    }
  }
}

internal fun TerminalOutput.assertEndsWithLines(expectedEndLines: List<String>) {
  val actualLines = getTextLines()
  Assertions.assertThat(actualLines).endsWith(expectedEndLines.toTypedArray())
}

internal fun TerminalOutput.assertStartsWithLines(expectedStartLines: List<String>) {
  val actualLines = getTextLines()
  Assertions.assertThat(actualLines).startsWith(*expectedStartLines.toTypedArray<String>())
}

internal class TerminalOutputLine(val outputChunks: List<TerminalOutputChunk>) {

  val lineText: String
    get() = outputChunks.joinToString(separator = "") { it.text }

  fun contains(substringToFind: String): Boolean = outputChunks.any {
    it.text.contains(substringToFind)
  }

  fun contains(chunkToFind: TerminalOutputChunk): Boolean = outputChunks.any {
    it.text == chunkToFind.text && it.style == chunkToFind.style
  }

  override fun toString(): String = lineText
}

internal data class TerminalOutputChunk(val text: String, val style: TextStyle)

private class TerminalOutputBuilder(
  private val textBuffer: TerminalTextBuffer,
  private val terminal: Terminal,
  private val trimLineEnds: Boolean,
) {

  private val lines: MutableList<TerminalOutputLine> = mutableListOf()
  private var currentLine: MutableList<TerminalOutputChunk> = mutableListOf()
  private var previousLineWrapped: Boolean = true

  fun build(): TerminalOutput {
    textBuffer.modify {
      val cursorLineInd = terminal.cursorPosition.y - 1
      for (ind in -textBuffer.historyLinesCount .. cursorLineInd) {
        processLine(textBuffer.getLine(ind))
      }
    }
    addCurrentLine()
    return TerminalOutput(lines)
  }

  private fun processLine(line: TerminalLine) {
    if (!previousLineWrapped) {
      addCurrentLine()
    }
    line.forEachEntry { entry ->
      val text = entry.text.clearDWC()
      if (text.isNotEmpty() && (!entry.isNul || entry.style != TextStyle.EMPTY)) {
        val resultText = if (entry.isNul) " ".repeat(text.length) else text
        currentLine.add(TerminalOutputChunk(resultText, entry.style))
      }
    }
    previousLineWrapped = line.isWrapped
  }

  private fun addCurrentLine() {
    val mergedChunks = mergeConsecutiveChunksWithSameStyle(currentLine)
    val line = TerminalOutputLine(mergedChunks)
    lines.add(if (trimLineEnds) trimLineEnd(line) else line)
    currentLine = mutableListOf()
  }

  private fun mergeConsecutiveChunksWithSameStyle(chunks: List<TerminalOutputChunk>): List<TerminalOutputChunk> {
    var prev = chunks.firstOrNull() ?: return emptyList()
    return buildList(chunks.size) {
      for (chunk in chunks.asSequence().drop(1)) {
        if (prev.style != chunk.style) {
          add(prev)
          prev = chunk
        }
        else {
          prev = TerminalOutputChunk(prev.text + chunk.text, chunk.style)
        }
      }
      add(prev)
    }
  }

}

private fun trimLineEnd(line: TerminalOutputLine): TerminalOutputLine {
  val chunks = line.outputChunks.toMutableList()
  while (chunks.size > 1 && chunks.last().text.isBlank()) {
    chunks.removeLast()
  }
  if (chunks.isNotEmpty()) {
    val lastChunk = chunks.removeLast()
    chunks.add(TerminalOutputChunk(lastChunk.text.trimEnd(), lastChunk.style))
  }
  return TerminalOutputLine(chunks)
}

private fun CharBuffer.clearDWC(): String = this.toString().replace(CharUtils.DWC.toString(), "")

internal val DEFAULT_OUTPUT_TIMEOUT: Duration = 20.seconds
