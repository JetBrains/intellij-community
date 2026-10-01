// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.lang.LangBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.util.function.Consumer
import kotlin.io.path.extension
import kotlin.io.path.pathString

/**
 * Parses javac's output.
 */
@ApiStatus.Internal
class JavacOutputParser(private vararg val fileExtensions: String) : BuildOutputParser {

  constructor() : this("java")

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
    var colonIndex1 = line.indexOf(COLON)
    if (colonIndex1 == 1) { // drive letter
      colonIndex1 = line.indexOf(COLON, colonIndex1 + 1)
    }

    if (colonIndex1 >= 0) { // looks like found something like a file path.
      val part1 = line.substring(0, colonIndex1).trim()
      if (part1.equals("error", ignoreCase = true) /* jikes */ || part1.equals("Caused by", ignoreCase = true)) {
        // +1 so we don't include the colon
        val text = line.substring(colonIndex1 + 1).trim()
        messageConsumer.accept(
          MessageEvent.builder(text, MessageEvent.Kind.ERROR)
            .withParentId(reader.parentEventId)
            .withGroup(COMPILER_MESSAGES_GROUP)
            .withDescription(line)
            .build()
        )
        return true
      }
      if (part1.equals("warning", ignoreCase = true)) {
        // +1 so we don't include the colon
        val text = line.substring(colonIndex1 + 1).trim()
        messageConsumer.accept(
          MessageEvent.builder(text, MessageEvent.Kind.WARNING)
            .withParentId(reader.parentEventId)
            .withGroup(COMPILER_MESSAGES_GROUP)
            .withDescription(line)
            .build()
        )
        return true
      }
      if (part1.equals("javac", ignoreCase = true)) {
        messageConsumer.accept(
          MessageEvent.builder(line, MessageEvent.Kind.ERROR)
            .withParentId(reader.parentEventId)
            .withGroup(COMPILER_MESSAGES_GROUP)
            .withDescription(line)
            .build()
        )
        return true
      }
      if (part1.equals("Note", ignoreCase = true)) {
        val note = line.substring(colonIndex1 + 1).trim()
        val javaFileExtensionIndex = note.indexOf(JAVA_EXTENSION)
        if (javaFileExtensionIndex > 0) {
          val path = note.substring(0, javaFileExtensionIndex + JAVA_EXTENSION.length).toNioPathOrNull()
          if (path != null && Files.isRegularFile(path)) {
            val message = note.substring(javaFileExtensionIndex + JAVA_EXTENSION.length + 1)
            val detailedMessage = amendNextInfoLinesIfNeeded("${path.pathString}:\n$message", reader)
            messageConsumer.accept(
              MessageEvent.builder(message, MessageEvent.Kind.INFO)
                .withParentId(reader.parentEventId)
                .withGroup(COMPILER_MESSAGES_GROUP)
                .withDescription(detailedMessage)
                .withFilePosition(FilePosition(path, 0, 0))
                .build()
            )
            return true
          }
        }
      }

      val colonIndex2 = line.indexOf(COLON, colonIndex1 + 1)
      if (colonIndex2 >= 0) {
        val path = part1.toNioPathOrNull() ?: return false
        if (!Files.isRegularFile(path)) {
          // the part one is not a file path.
          return false
        }
        val lineNumber = line.substring(colonIndex1 + 1, colonIndex2).trim().toIntOrNull() // 1-based.
        if (lineNumber != null) {
          var text = line.substring(colonIndex2 + 1).trim()
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
            val nextLine = outputCollector.readLine() ?: return false
            if (nextLine.trim() == "^") {
              column = nextLine.indexOf('^')
              var messageEnd = outputCollector.readLine()

              while (messageEnd != null && messageEnd.isMessageEnd()) {
                messageList.add(messageEnd.trim())
                messageEnd = outputCollector.readLine()
              }

              if (messageEnd != null) {
                outputCollector.pushBack()
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
            val detailedMessage = "$line\n${outputCollector.getOutput()}".trimIndent()
            messageConsumer.accept(
              MessageEvent.builder(message, kind)
                .withParentId(reader.parentEventId)
                .withGroup(COMPILER_MESSAGES_GROUP)
                .withDescription(detailedMessage)
                .withFilePosition(FilePosition(path, lineNumber - 1, column))
                .build()
            )
            return true
          }
        }
      }
    }

    if (line.endsWith("java.lang.OutOfMemoryError")) {
      messageConsumer.accept(
        MessageEvent.builder(LangBundle.message("build.event.message.out.memory"), MessageEvent.Kind.ERROR)
          .withParentId(reader.parentEventId)
          .withGroup(COMPILER_MESSAGES_GROUP)
          .withDescription(line)
          .build()
      )
      return true
    }

    return false
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

    private fun amendNextInfoLinesIfNeeded(str: String, reader: BuildOutputInstantReader): @NlsSafe String = buildString {
      append(str)
      var nextLine = reader.readLine()
      while (nextLine != null) {
        if (nextLine.startsWith(NOTE_LINE_PREFIX) && !nextLine.contains(JAVA_EXTENSION)) {
          append("\n").append(nextLine.substring(NOTE_LINE_PREFIX.length))
          nextLine = reader.readLine()
          continue
        }
        reader.pushBack()
        break
      }
    }

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
  }
}
