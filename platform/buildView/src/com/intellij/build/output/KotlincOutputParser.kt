// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.MessageEventBuilder
import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputId
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.lang.LangBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NonNls
import java.net.URI
import java.util.Locale
import java.util.function.Consumer
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.toPath

/**
 * Parses kotlinc's output.
 */
@ApiStatus.Internal
class KotlincOutputParser : BuildOutputLineParserBridge() {

  @ApiStatus.Internal
  companion object {
    private val COMPILER_MESSAGES_GROUP: @BuildEventsNls.Title String
      @BuildEventsNls.Title
      get() = LangBundle.message("build.event.title.kotlin.compiler")

    private val WINDOWS_PATH = "^\\s*.:(/|\\\\)".toRegex()
    private val WINDOWS_URI = "^\\s*file:/+.:(/|\\\\)".toRegex()
    private val UNIX_URI = "^\\s*file:/".toRegex()

    fun extractPath(line: String): String? {
      if (!line.contains(":")) return null
      val systemPrefixLen = listOf(WINDOWS_PATH, WINDOWS_URI, UNIX_URI).firstNotNullOfOrNull {
        it.find(line)?.groups?.last()?.range?.endInclusive
      } ?: 0

      val colonIndex = line.indexOf(':', systemPrefixLen)
      if (colonIndex < 0) return null

      return line.substring(0, colonIndex)
    }
  }

  override suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ): Boolean {
    val colonIndex1 = line.text.colon()

    val severity = if (colonIndex1 >= 0) line.text.substringBeforeAndTrim(colonIndex1) else return false
    if (!severity.startsWithSeverityPrefix()) return false

    val lineWoSeverity = line.text.substringAfterAndTrim(colonIndex1)
    val path = extractPath(lineWoSeverity) ?: return false

    val file = when (path.startsWith("file:")) {
      true -> runCatching { URI(path).toPath() }.getOrNull()
      else -> path.toNioPathOrNull()
    }

    val fileExtension = file?.extension?.lowercase(Locale.getDefault())
    if (file == null || !file.isRegularFile() || (fileExtension != "kt" && fileExtension != "kts" && fileExtension != "java")) {
      multicaster.notifyBuildEvent(
        MessageEvent.builder(lineWoSeverity.trim(), getMessageKind(severity)) //NON-NLS
          .withParentId(taskId)
          .withGroup(COMPILER_MESSAGES_GROUP)
          .withDescription(lineWoSeverity.amendNextLinesIfNeeded(line.ids, reader), isSingleFlow)
          .build()
      )
      return true
    }

    val lineWoPath = lineWoSeverity.substringAfterAndTrim(path.length)
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
    } else if (URI_POSITION_PATTERN.toRegex().find(lineWoPath) != null) {
      val parts = URI_POSITION_PATTERN.toRegex().find(lineWoPath)!!
      val position = parts.groupValues.first()
      lineWoPositionIndex = position.length
      matcher = URI_POSITION_PATTERN.matcher(position)
    } else {
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
      val relatedNextLines = "".amendNextLinesIfNeeded(line.ids, reader)
      val message = lineWoPath.substringAfterAndTrim(lineWoPositionIndex) + relatedNextLines.text
      val details = (listOf(line) + relatedNextLines).joinToText()

      if (matcher != null && matcher.matches()) {
        val lineNumber = matcher.group(1)
        val symbolNumber = if (matcher.groupCount() >= 2) matcher.group(2) else "1"
        if (lineNumber != null) {
          val symbolNumberText = symbolNumber.toInt()
          multicaster.notifyBuildEvent(
            MessageEvent.builder(message.trim(), getMessageKind(severity)) //NON-NLS
              .withParentId(taskId)
              .withGroup(COMPILER_MESSAGES_GROUP)
              .withDescription(details, isSingleFlow)
              .withFilePosition(FilePosition(file, lineNumber.toInt() - 1, symbolNumberText - 1))
              .build()
          )
          return true
        }
      }

      multicaster.notifyBuildEvent(
        MessageEvent.builder(message.trim(), getMessageKind(severity)) //NON-NLS
          .withParentId(taskId)
          .withGroup(COMPILER_MESSAGES_GROUP)
          .withDescription(details, isSingleFlow)
          .build()
      )
      return true
    }
    else {
      multicaster.notifyBuildEvent(
        MessageEvent.builder(lineWoSeverity.trim(), getMessageKind(severity)) //NON-NLS
          .withParentId(taskId)
          .withGroup(COMPILER_MESSAGES_GROUP)
          .withDescription(lineWoSeverity.amendNextLinesIfNeeded(line.ids, reader), isSingleFlow)
          .build()
      )
      return true
    }
  }

  private val COLON = ":"
  private val KOTLIN_POSITION_PATTERN = Pattern.compile("\\(([0-9]*), ([0-9]*)\\)")
  private val URI_POSITION_PATTERN = Pattern.compile("^([0-9]*):([0-9]*)")
  private val JAVAC_POSITION_PATTERN = Pattern.compile("([0-9]+)")
  private val LINE_COLON_COLUMN_POSITION_PATTERN = Pattern.compile("([0-9]*):([0-9]*)")

  private suspend fun String.amendNextLinesIfNeeded(
    sourceLineOutputIds: List<OutputId>,
    reader: BuildOutputReplayableLineReader,
  ): BuildOutputText = buildList {
    add(BuildOutputText.createOutputText(sourceLineOutputIds, this@amendNextLinesIfNeeded))

    var nextLine = reader.readLine()
    while (nextLine != null) {
      if (nextLine.text.isNextMessage()) {
        reader.pushBack(1)
        break
      }
      else {
        add(nextLine)
        nextLine = reader.readLine()
      }
    }
  }.joinToText()

  private fun List<BuildOutputText>.joinToText(): BuildOutputText =
    BuildOutputText.createOutputText(
      flatMap { it.ids },
      map { it.text }
        .filter { it.isNotBlank() }
        .joinToString()
    )

  private fun MessageEventBuilder.withDescription(line: BuildOutputText, isSingleFlow: Boolean): MessageEventBuilder =
    when (isSingleFlow) {
      true -> withOutputIds(line.ids)
      else -> withDescription(line.text)
    }

  private fun String.isNextMessage(): Boolean {
    val colonIndex1 = indexOf(COLON)
    return colonIndex1 == 0
           || (colonIndex1 >= 0 && substring(0, colonIndex1).startsWithSeverityPrefix()) // Next Kotlin message
           || StringUtil.startsWith(this, "Note: ") // Next javac info message candidate //NON-NLS
           || StringUtil.startsWith(this, "> Task :") // Next gradle message candidate //NON-NLS
           || (!StringUtil.containsIgnoreCase(this, "AutoMigration Failure")
               && StringUtil.containsIgnoreCase(this, "FAILURE")) //NON-NLS
           || StringUtil.containsIgnoreCase(this, "FAILED") //NON-NLS
           || StringUtil.contains(this, "BUILD SUCCESSFUL") //NON-NLS
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

  @Contract(pure = true)
  private fun String.substringAfterAndTrim(index: Int) = substring(index + 1).trim()
  @Contract(pure = true)
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

  private fun Consumer<in MessageEvent>.notifyMessageEvent(message: MessageEvent) {
    // Ignore KaptError.ERROR_RAISED message from kapt. We already processed all errors from annotation processing
    if (!isKaptErrorWhileAnnotationProcessing(message)) {
      accept(message)
    }
  }
}