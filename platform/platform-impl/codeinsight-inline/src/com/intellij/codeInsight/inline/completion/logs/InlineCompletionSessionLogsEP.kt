// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
import com.intellij.codeInsight.inline.completion.statistics.LocalStatistics
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * A container for a group of logs related to the Inline completion session [phase].
 * Provides features and the phase for the FUS event registration in the [InlineCompletionLogs].
 *
 */
@ApiStatus.Internal
abstract class PhasedLogs(val phase: Phase) {
  private val fields = mutableListOf<EventFieldExt<*>>()

  val registeredFields: List<EventFieldExt<*>>
    get() = fields

  /**
   * Associate the given [field] with the [phase].
   * Such a log will be sent only for a fraction of requests in the release build.
   */
  protected fun <T> register(field: EventField<T>): EventField<T> {
    fields.add(EventFieldExt(field, false))
    return field
  }

  /**
   * Associate the given [field] with the [phase].
   * Such a log will always be sent;
   * Important: try to keep the number of basic fields as small as possible
   */
  protected fun <T> registerBasic(field: EventField<T>): EventField<T> {
    fields.add(EventFieldExt(field, true))
    return field
  }

  protected fun <T> EventField<T>.alsoLocalStatistic(): EventField<T> {
    LocalStatistics.Schema.register(this)
    return this
  }
}

/**
 * Wrapper around the [EventField] with an additional property
 */
@ApiStatus.Internal
data class EventFieldExt<T>(
  val field: EventField<T>,
  val isBasic: Boolean = false,
)

@ApiStatus.Internal
interface InlineCompletionSessionLogsEP {

  val logGroups: List<PhasedLogs>

  companion object {
    val EP_NAME = ExtensionPointName<InlineCompletionSessionLogsEP>("com.intellij.inline.completion.session.logs");
  }
}