// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.util.text.StringUtil
import java.io.File
import java.lang.IllegalStateException
import java.util.function.Consumer
import java.util.regex.Pattern


/**
 * TODO should be moved to the kotlin plugin
 *
 * Parses kotlinc's output.
 */
class KotlincOutputParser : BuildOutputParser {

  companion object {
    private val COMPILER_MESSAGES_GROUP = "Kotlin compiler"
  }

  override fun parse(line: String, reader: BuildOutputInstantReader, consumer: Consumer<MessageEvent>): Boolean {
    val colonIndex1 = line.colon()

    val severity = if (colonIndex1 >= 0) line.substringBeforeAndTrim(colonIndex1) else return false
    if (!severity.startsWithSeverityPrefix()) return false

    val lineWoSeverity = line.substringAfterAndTrim(colonIndex1)
    val colonIndex2 = lineWoSeverity.colon().skipDriveOnWin(lineWoSeverity)
    if (colonIndex2 >= 0) {
      val path = lineWoSeverity.substringBeforeAndTrim(colonIndex2)
      val file = File(path)

      val fileExtension = file.extension.toLowerCase()
      if (!file.isFile || (fileExtension != "kt" && fileExtension != "java")) {
        return addMessage(createMessage(reader.buildId, getMessageKind(severity), lineWoSeverity.amendNextLinesIfNeeded(reader)), consumer)
      }

      val lineWoPath = lineWoSeverity.substringAfterAndTrim(colonIndex2)
      val colonIndex3 = lineWoPath.colon()
      if (colonIndex3 >= 0) {
        val position = lineWoPath.substringBeforeAndTrim(colonIndex3)

        val matcher = KOTLIN_POSITION_PATTERN.matcher(position).takeIf { it.matches() } ?: JAVAC_POSITION_PATTERN.matcher(position)
        val message = lineWoPath.substringAfterAndTrim(colonIndex3).amendNextLinesIfNeeded(reader)

        if (matcher.matches()) {
          val lineNumber = matcher.group(1)
          val symbolNumber = if (matcher.groupCount() >= 2) matcher.group(2) else "1"
          if (lineNumber != null) {
            val symbolNumberText = symbolNumber.toInt()
            return addMessage(createMessageWithLocation(
              reader.buildId, getMessageKind(severity), message, path, lineNumber.toInt(), symbolNumberText), consumer)
          }
        }

        return addMessage(createMessage(reader.buildId, getMessageKind(severity), message), consumer)
      }
      else {
        return addMessage(createMessage(reader.buildId, getMessageKind(severity), lineWoSeverity.amendNextLinesIfNeeded(reader)), consumer)
      }
    }

    return false
  }

  private val COLON = ":"
  private val KOTLIN_POSITION_PATTERN = Pattern.compile("\\(([0-9]*), ([0-9]*)\\)")
  private val JAVAC_POSITION_PATTERN = Pattern.compile("([0-9]+)")

  private fun String.amendNextLinesIfNeeded(reader: BuildOutputInstantReader): String {
    var nextLine = reader.readLine()

    val builder = StringBuilder(this)
    while (nextLine != null && nextLine.isNextMessage().not()) {
      builder.append("\n").append(nextLine)
      nextLine = reader.readLine()
    }

    if (nextLine != null) {
      // This code is needed for compatibility with AS 2.0 and IDEA 15.0, because of difference in android plugins
      val positionField = try {
        reader::class.java.getDeclaredField("myPosition")
      }
      catch (e: Throwable) {
        null
      }
      if (positionField != null) {
        positionField.isAccessible = true
        positionField.setInt(reader, positionField.getInt(reader) - 1)
      }
    }

    return builder.toString()
  }

  private fun String.isNextMessage(): Boolean {
    val colonIndex1 = indexOf(COLON)
    return colonIndex1 == 0
           || (colonIndex1 >= 0 && substring(0, colonIndex1).startsWithSeverityPrefix()) // Next Kotlin message
           || StringUtil.containsIgnoreCase(this, "FAILURE")
           || StringUtil.containsIgnoreCase(this, "FAILED")
  }

  private fun String.startsWithSeverityPrefix() = getMessageKind(this) != MessageEvent.Kind.SIMPLE

  private fun getMessageKind(kind: String) = when (kind) {
    "e" -> MessageEvent.Kind.ERROR
    "w" -> MessageEvent.Kind.WARNING
    "i" -> MessageEvent.Kind.INFO
    "v" -> MessageEvent.Kind.SIMPLE
    else -> MessageEvent.Kind.SIMPLE
  }

  private fun String.substringAfterAndTrim(index: Int) = substring(index + 1).trim()
  private fun String.substringBeforeAndTrim(index: Int) = substring(0, index).trim()
  private fun String.colon() = indexOf(COLON)
  private fun Int.skipDriveOnWin(line: String): Int {
    return if (this == 1) line.indexOf(COLON, this + 1) else this
  }

  private val KAPT_ERROR_WHILE_ANNOTATION_PROCESSING_MARKER_TEXT =
    // KaptError::class.java.canonicalName + ": " + KaptError.Kind.ERROR_RAISED.message
    "org.jetbrains.kotlin.kapt3.diagnostic.KaptError" + ": " + "Error while annotation processing"

  private fun isKaptErrorWhileAnnotationProcessing(message: MessageEvent): Boolean {
    if (message.kind != MessageEvent.Kind.ERROR) return false

    val messageText = message.message
    return messageText.startsWith(IllegalStateException::class.java.name)
           && messageText.contains(KAPT_ERROR_WHILE_ANNOTATION_PROCESSING_MARKER_TEXT)
  }

  private fun addMessage(message: MessageEvent, consumer: Consumer<MessageEvent>): Boolean {
    // Ignore KaptError.ERROR_RAISED message from kapt. We already processed all errors from annotation processing
    if (isKaptErrorWhileAnnotationProcessing(message)) return true
    consumer.accept(message)
    return true
  }

  private fun createMessage(buildId: Any, messageKind: MessageEvent.Kind, text: String): MessageEvent {
    return MessageEventImpl(buildId, messageKind, COMPILER_MESSAGES_GROUP, text.trim())
  }

  private fun createMessageWithLocation(
    buildId: Any,
    messageKind: MessageEvent.Kind,
    text: String,
    file: String,
    lineNumber: Int,
    columnIndex: Int
  ): FileMessageEventImpl {
    return FileMessageEventImpl(buildId, messageKind, COMPILER_MESSAGES_GROUP, text.trim(),
                                FilePosition(File(file), lineNumber - 1, columnIndex - 1))
  }

}