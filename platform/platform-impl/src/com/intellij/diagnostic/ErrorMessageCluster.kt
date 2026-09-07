// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.ProblematicPluginInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId

private val LOG = logger<ErrorMessageCluster>()

/**
 * Describes a group of errors with the same stacktrace in [IdeErrorsDialog].
 */
internal class ErrorMessageCluster(
  val messages: List<AbstractMessage>,
  val pluginId: PluginId?,
  val pluginInfo: ProblematicPluginInfo?,
  val submitter: ErrorReportSubmitter?,
) {
  val first = messages.first()

  @Volatile
  var detailsText: String? = detailsText()

  private fun detailsText(): String? {
    val t = first.throwable
    if (t is MessagePool.TooManyErrorsException) {
      return t.message
    }
    val userMessage = first.message
    val stacktrace = first.throwableText
    return if (userMessage.isNullOrBlank()) stacktrace else "${userMessage}\n\n${stacktrace}"
  }

  val isUnsent: Boolean get() = !first.isSubmitted && !first.isSubmitting

  val canSubmit: Boolean get() = submitter != null && isUnsent

  /**
   * The event for a report that a user sends.
   * Returns `null` if the text no longer holds a stacktrace that [decouple] can read.
   */
  fun toUserLoggingEvent(): IdeaLoggingEvent? {
    val (comment, throwable) = decouple() ?: return null
    return first.toLoggingEvent(AbstractMessage.ReportKind.USER, pluginInfo, comment, throwable)
  }

  fun decouple(): Pair<String?, Throwable>? {
    val detailsText = detailsText ?: run {
      // The dialog disables the "Submit" button for an empty text, so a caller must not reach this line.
      LOG.warn("The cluster of ${first.throwable.javaClass.name} holds no text, so no report can go out")
      return null
    }
    val originalThrowableText = first.throwableText
    val originalThrowableClass = first.throwable.javaClass.name

    val p1 = detailsText.indexOf(originalThrowableText)
    if (p1 >= 0) {
      val message = detailsText.substring(0, p1).trim { it <= ' ' }.takeIf(String::isNotEmpty)
      return message to first.throwable
    }

    if (detailsText.startsWith(originalThrowableClass)) {
      return null to RecoveredThrowable.fromString(detailsText)
    }

    val p2 = detailsText.indexOf('\n' + originalThrowableClass)
    if (p2 >= 0) {
      val message = detailsText.substring(0, p2).trim { it <= ' ' }.takeIf(String::isNotEmpty)
      return message to RecoveredThrowable.fromString(detailsText.substring(p2 + 1))
    }

    return null
  }
}