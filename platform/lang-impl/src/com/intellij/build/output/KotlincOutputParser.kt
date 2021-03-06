// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.lang.LangBundle
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.NonNls
import java.io.File
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * Parses kotlinc's output.
 */
class KotlincOutputParser : BuildOutputParser {

  companion object {
    private val COMPILER_MESSAGES_GROUP: @BuildEventsNls.Title String
      @BuildEventsNls.Title
      get() = LangBundle.message("build.event.title.kotlin.compiler")
  }

  override fun parse(line: String, reader: BuildOutputInstantReader, consumer: Consumer<in BuildEvent>): Boolean {
    val colonIndex1 = line.colon()

    val severity = if (colonIndex1 >= 0) line.substringBeforeAndTrim(colonIndex1) else return false
    if (!severity.startsWithSeverityPrefix()) return false

    val lineWoSeverity = line.substringAfterAndTrim(colonIndex1)
    val colonIndex2 = lineWoSeverity.colon().skipDriveOnWin(lineWoSeverity)
    if (colonIndex2 < 0) return false

    val path = lineWoSeverity.substringBeforeAndTrim(colonIndex2)
    val file = File(path)

    val fileExtension = file.extension.toLowerCase()
    if (!file.isFile || (fileExtension != "kt" && fileExtension != "kts" && fileExtension != "java")) { //NON-NLS
      val combinedMessage = lineWoSeverity.amendNextLinesIfNeeded(reader)
      return addMessage(createMessage(reader.parentEventId, getMessageKind(severity), lineWoSeverity, combinedMessage), consumer)
    }

    val lineWoPath = lineWoSeverity.substringAfterAndTrim(colonIndex2)
    var lineWoPositionIndex = -1
    var matcher: Matcher? = null
    if (lineWoPath.startsWith('(')) {
      val colonIndex3 = lineWoPath.colon()
      if (colonIndex3 >= 0) {
        lineWoPositionIndex = colonIndex3
      }
      if (lineWoPositionIndex >= 0) {
        val position = lineWoPath.substringBeforeAndTrim(lineWoPositionIndex)
        matcher = KOTLIN_POSITION_PATTERN.matcher(position).takeIf { it.matches() } ?: JAVAC_POSITION_PATTERN.matcher(position)
      }
    }
    else {
      val colonIndex4 = lineWoPath.colon(1)
      if (colonIndex4 >= 0) {
        lineWoPositionIndex = colonIndex4
      }
      else {
        lineWoPositionIndex = lineWoPath.colon()
      }

      if (lineWoPositionIndex >= 0) {
        val position = lineWoPath.substringBeforeAndTrim(colonIndex4)
        matcher = LINE_COLON_COLUMN_POSITION_PATTERN.matcher(position).takeIf { it.matches() } ?: JAVAC_POSITION_PATTERN.matcher(position)
      }
    }
    if (lineWoPositionIndex >= 0) {
      val relatedNextLines = "".amendNextLinesIfNeeded(reader)
      val message = lineWoPath.substringAfterAndTrim(lineWoPositionIndex) + relatedNextLines
      val details = line + relatedNextLines

      if (matcher != null && matcher.matches()) {
        val lineNumber = matcher.group(1)
        val symbolNumber = if (matcher.groupCount() >= 2) matcher.group(2) else "1"
        if (lineNumber != null) {
          val symbolNumberText = symbolNumber.toInt()
          return addMessage(createMessageWithLocation(
            reader.parentEventId, getMessageKind(severity), message, path, lineNumber.toInt(), symbolNumberText, details), consumer)
        }
      }

      return addMessage(createMessage(reader.parentEventId, getMessageKind(severity), message, details), consumer)
    }
    else {
      val combinedMessage = lineWoSeverity.amendNextLinesIfNeeded(reader)
      return addMessage(createMessage(reader.parentEventId, getMessageKind(severity), lineWoSeverity, combinedMessage), consumer)
    }
  }

  private val COLON = ":"
  private val KOTLIN_POSITION_PATTERN = Pattern.compile("\\(([0-9]*), ([0-9]*)\\)")
  private val JAVAC_POSITION_PATTERN = Pattern.compile("([0-9]+)")
  private val LINE_COLON_COLUMN_POSITION_PATTERN = Pattern.compile("([0-9]*):([0-9]*)")

  private fun String.amendNextLinesIfNeeded(reader: BuildOutputInstantReader): String {
    var nextLine = reader.readLine()

    val builder = StringBuilder(this)
    while (nextLine != null) {
      if (nextLine.isNextMessage()) {
        reader.pushBack()
        break
      }
      else {
        builder.append("\n").append(nextLine)
        nextLine = reader.readLine()
      }
    }
    return builder.toString()
  }

  private fun String.isNextMessage(): Boolean {
    val colonIndex1 = indexOf(COLON)
    return colonIndex1 == 0
           || (colonIndex1 >= 0 && substring(0, colonIndex1).startsWithSeverityPrefix()) // Next Kotlin message
           || StringUtil.startsWith(this, "Note: ") // Next javac info message candidate //NON-NLS
           || StringUtil.startsWith(this, "> Task :") // Next gradle message candidate //NON-NLS
           || StringUtil.containsIgnoreCase(this, "FAILURE") //NON-NLS
           || StringUtil.containsIgnoreCase(this, "FAILED") //NON-NLS
  }

  private fun String.startsWithSeverityPrefix() = getMessageKind(this) != MessageEvent.Kind.SIMPLE

  @NonNls
  private fun getMessageKind(kind: @NonNls String) = when (kind) {
    "e" -> MessageEvent.Kind.ERROR
    "w" -> MessageEvent.Kind.WARNING
    "i" -> MessageEvent.Kind.INFO
    "v" -> MessageEvent.Kind.SIMPLE
    else -> MessageEvent.Kind.SIMPLE
  }

  private fun String.substringAfterAndTrim(index: Int) = substring(index + 1).trim()
  private fun String.substringBeforeAndTrim(index: Int) = substring(0, index).trim()
  private fun String.colon() = indexOf(COLON)
  private fun String.colon(skip: Int): Int {
    var index = -1
    repeat(skip + 1) {
      index = indexOf(COLON, index + 1)
      if (index < 0) return index
    }
    return index
  }

  private fun Int.skipDriveOnWin(line: String): Int {
    return if (this == 1) line.indexOf(COLON, this + 1) else this
  }

  private val KAPT_ERROR_WHILE_ANNOTATION_PROCESSING_MARKER_TEXT
    get() =
      // KaptError::class.java.canonicalName + ": " + KaptError.Kind.ERROR_RAISED.message
      "org.jetbrains.kotlin.kapt3.diagnostic.KaptError" + ": " + LangBundle.message("kapterror.error.while.annotation.processing")

  private fun isKaptErrorWhileAnnotationProcessing(message: MessageEvent): Boolean {
    if (message.kind != MessageEvent.Kind.ERROR) return false

    val messageText = message.message
    return messageText.startsWith(IllegalStateException::class.java.name)
           && messageText.contains(KAPT_ERROR_WHILE_ANNOTATION_PROCESSING_MARKER_TEXT)
  }

  private fun addMessage(message: MessageEvent, consumer: Consumer<in MessageEvent>): Boolean {
    // Ignore KaptError.ERROR_RAISED message from kapt. We already processed all errors from annotation processing
    if (isKaptErrorWhileAnnotationProcessing(message)) return true
    consumer.accept(message)
    return true
  }

  private fun createMessage(parentId: Any,
                            messageKind: MessageEvent.Kind,
                            text: @BuildEventsNls.Message String,
                            detail: @BuildEventsNls.Description String): MessageEvent {
    return MessageEventImpl(parentId, messageKind, COMPILER_MESSAGES_GROUP, text.trim(), detail) //NON-NLS
  }

  private fun createMessageWithLocation(
    parentId: Any,
    messageKind: MessageEvent.Kind,
    text: @BuildEventsNls.Message String,
    file: String,
    lineNumber: Int,
    columnIndex: Int,
    detail: @BuildEventsNls.Description String
  ): FileMessageEventImpl {
    return FileMessageEventImpl(parentId, messageKind, COMPILER_MESSAGES_GROUP, text.trim(), detail, //NON-NLS
                                FilePosition(File(file), lineNumber - 1, columnIndex - 1))
  }

}