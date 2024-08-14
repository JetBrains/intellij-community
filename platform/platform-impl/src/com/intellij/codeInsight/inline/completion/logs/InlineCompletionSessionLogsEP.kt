// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionLogsContainer.Phase
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
  private val _fields = mutableListOf<EventField<*>>()

  val fields: List<EventField<*>>
    get() = _fields

  /**
   * Associate the given [field] with the [phase]
   */
  protected fun<T> register(field: EventField<T>): EventField<T> {
    _fields.add(field)
    return field
  }

}

@ApiStatus.Internal
interface InlineCompletionSessionLogsEP {

  val fields: List<PhasedLogs> // todo rename

  companion object {
    val EP_NAME = ExtensionPointName<InlineCompletionSessionLogsEP>("com.intellij.inline.completion.session.logs");
  }
}