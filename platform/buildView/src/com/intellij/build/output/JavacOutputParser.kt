// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.MessageEventBuilder
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.lang.LangBundle
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.util.LinkedList
import kotlin.io.path.extension

/**
 * Parses javac's output.
 */
@ApiStatus.Internal
class JavacOutputParser(private vararg val fileExtensions: String) : BuildOutputLineParserBridge() {

  constructor() : this("java")

  override suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ): Boolean {
    var colonIndex1 = line.text.indexOf(COLON)
    if (colonIndex1 == 1) { // drive letter
      colonIndex1 = line.text.indexOf(COLON, colonIndex1 + 1)
    }

    if (colonIndex1 >= 0) { // looks like found something like a file path.
      val part1 = line.text.substring(0, colonIndex1).trim()
      if (part1.equals("error", ignoreCase = true) /* jikes */ || part1.equals("Caused by", ignoreCase = true)) {
        // +1 so we don't include the colon
        val text = line.text.substring(colonIndex1 + 1).trim()
        multicaster.notifyBuildEvent(
          MessageEvent.builder(text, MessageEvent.Kind.ERROR)
            .withParentId(taskId)
            .withGroup(COMPILER_MESSAGES_GROUP)
            .withDescription(line, isSingleFlow)
            .build()
        )
        return true
      }
      if (part1.equals("warning", ignoreCase = true)) {
        // +1 so we don't include the colon
        val text = line.text.substring(colonIndex1 + 1).trim()
        multicaster.notifyBuildEvent(
          MessageEvent.builder(text, MessageEvent.Kind.WARNING)
            .withParentId(taskId)
            .withGroup(COMPILER_MESSAGES_GROUP)
            .withDescription(line, isSingleFlow)
            .build()
        )
        return true
      }
      if (part1.equals("javac", ignoreCase = true)) {
        multicaster.notifyBuildEvent(
          MessageEvent.builder(line.text, MessageEvent.Kind.ERROR)
            .withParentId(taskId)
            .withGroup(COMPILER_MESSAGES_GROUP)
            .withDescription(line, isSingleFlow)
            .build()
        )
        return true
      }
      if (part1.equals("Note", ignoreCase = true)) {
        val note = line.text.substring(colonIndex1 + 1).trim()
        val javaFileExtensionIndex = note.indexOf(JAVA_EXTENSION)
        if (javaFileExtensionIndex > 0) {
          val path = note.substring(0, javaFileExtensionIndex + JAVA_EXTENSION.length).toNioPathOrNull()
          if (path != null && Files.isRegularFile(path)) {
            val message = note.substring(javaFileExtensionIndex + JAVA_EXTENSION.length + 1)
            val detailedMessage = amendNextInfoLinesIfNeeded(line, reader)
            multicaster.notifyBuildEvent(
              MessageEvent.builder(message, MessageEvent.Kind.INFO)
                .withParentId(taskId)
                .withGroup(COMPILER_MESSAGES_GROUP)
                .withDescription(detailedMessage, isSingleFlow)
                .withFilePosition(FilePosition(path, 0, 0))
                .build()
            )
            return true
          }
        }
      }

      val colonIndex2 = line.text.indexOf(COLON, colonIndex1 + 1)
      if (colonIndex2 >= 0) {
        val path = part1.toNioPathOrNull() ?: return false
        if (!Files.isRegularFile(path)) {
          // the part one is not a file path.
          return false
        }
        val lineNumber = line.text.substring(colonIndex1 + 1, colonIndex2).trim().toIntOrNull() // 1-based.
        if (lineNumber != null) {
          var text = line.text.substring(colonIndex2 + 1).trim()
          var kind = MessageEvent.Kind.ERROR

          if (text.startsWith(WARNING_PREFIX, ignoreCase = true)) {
            text = text.substring(WARNING_PREFIX.length).trim()
            kind = MessageEvent.Kind.WARNING
          }
          else if (text.startsWith(NOTE_PREFIX, ignoreCase = true)) {
            text = text.substring(NOTE_PREFIX.length).trim()
            kind = MessageEvent.Kind.INFO
          }
          else if (text.startsWith(ERROR_PREFIX, ignoreCase = true)) {
            text = text.substring(ERROR_PREFIX.length).trim()
            kind = MessageEvent.Kind.ERROR
          }

          // Only slurp up line pointer (^) information if this is required source files
          if (!fileExtensions.any { FileUtil.namesEqual(path.extension, it) }) {
            return false
          }

          val outputCollector = BuildOutputCollector(reader)
          val messageList = mutableListOf(text)
          var column: Int // 0-based.
          var prevLine: String? = null
          while (true) {
            val nextLine = outputCollector.readLine()?.text ?: return false
            if (nextLine.trim() == "^") {
              column = nextLine.indexOf('^')
              var messageEnd = outputCollector.readLine()?.text

              while (messageEnd != null && messageEnd.isMessageEnd()) {
                messageList.add(messageEnd.trim())
                messageEnd = outputCollector.readLine()?.text
              }

              if (messageEnd != null) {
                outputCollector.pushBack(1)
              }
              break
            }
            if (prevLine != null) {
              messageList.add(prevLine)
            }
            prevLine = nextLine
          }

          if (column >= 0) {
            val message = convertMessages(messageList).joinToString("\n")
            val detailedMessage = (listOf(line) + outputCollector.getOutput()).joinToText("\n")
            multicaster.notifyBuildEvent(
              MessageEvent.builder(message, kind)
                .withParentId(taskId)
                .withGroup(COMPILER_MESSAGES_GROUP)
                .withDescription(detailedMessage, isSingleFlow)
                .withFilePosition(FilePosition(path, lineNumber - 1, column))
                .build()
            )
            return true
          }
        }
      }
    }

    if (line.text.endsWith("java.lang.OutOfMemoryError")) {
      multicaster.notifyBuildEvent(
        MessageEvent.builder(LangBundle.message("build.event.message.out.memory"), MessageEvent.Kind.ERROR)
          .withParentId(taskId)
          .withGroup(COMPILER_MESSAGES_GROUP)
          .withDescription(line, isSingleFlow)
          .build()
      )
      return true
    }

    return false
  }

  private class BuildOutputCollector(
    private val reader: BuildOutputReplayableLineReader,
  ) : BuildOutputReplayableLineReader {

    private val readLines = LinkedList<BuildOutputText>()

    override suspend fun readLine(): BuildOutputText? {
      val line = reader.readLine() ?: return null
      readLines.add(line)
      return line
    }

    override fun pushBack(numberOfLines: Int) {
      reader.pushBack(numberOfLines)
      repeat(numberOfLines) { readLines.pollLast() ?: return@repeat }
    }

    fun getOutput(): List<BuildOutputText> = readLines
  }

  private companion object {
    private const val COLON = ':'

    private const val JAVA_EXTENSION = ".java"

    private const val WARNING_PREFIX = "warning:" // default value
    private const val NOTE_PREFIX = "note:"
    private const val ERROR_PREFIX = "error:"
    private const val NOTE_LINE_PREFIX = "Note: "

    private val COMPILER_MESSAGES_GROUP: @BuildEventsNls.Title String
      get() = LangBundle.message("build.event.title.compiler")

    private suspend fun amendNextInfoLinesIfNeeded(line: BuildOutputText, reader: BuildOutputReplayableLineReader): BuildOutputText =
      buildList {
        add(line)
        var nextLine: BuildOutputText? = reader.readLine()
        while (nextLine != null) {
          if (nextLine.text.startsWith(NOTE_LINE_PREFIX) && !nextLine.text.contains(JAVA_EXTENSION)) {
            add(nextLine)
            nextLine = reader.readLine()
            continue
          }
          reader.pushBack(1)
          break
        }
      }.joinToText("\n")

    private fun String.isMessageEnd(): Boolean = firstOrNull()?.isWhitespace() == true

    private fun convertMessages(messages: MutableList<String>): List<String> {
      if (messages.size <= 1) {
        return messages
      }
      val line0 = messages[0]
      val line1 = messages[1]
      val colonIndex = line1.indexOf(':')
      if (colonIndex > 0) {
        val part1 = line1.substring(0, colonIndex).trim()
        // jikes
        if (part1 == "symbol") {
          val symbol = line1.substring(colonIndex + 1).trim()
          messages.removeAt(1)
          if (messages.size >= 2) {
            messages.removeAt(1)
          }
          messages[0] = "$line0 $symbol"
        }
      }
      return messages
    }

    private fun List<BuildOutputText>.joinToText(separator: String = ", "): BuildOutputText =
      BuildOutputText.createOutputText(
        flatMap { it.ids },
        map { it.text }
          .filter { it.isNotBlank() }
          .joinToString(separator = separator) { it }
      )

    private fun MessageEventBuilder.withDescription(line: BuildOutputText, isSingleFlow: Boolean): MessageEventBuilder =
      when (isSingleFlow) {
        true -> withOutputIds(line.ids)
        else -> withDescription(line.text)
      }
  }
}
