// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import org.jetbrains.annotations.ApiStatus
import kotlin.collections.component1
import kotlin.collections.component2


// TODO: This class was introduced for handling legal issues related to sensitive statistic collectors (specifically for ML_FED collector).
//       We should not use it in any other cases and it should be removed
//       when we declare events from InlineCompletionLogs.Session as FeatureDeclarations
@ApiStatus.Internal
class CustomRequestIdLogger(
  private val customRequestId: Long,
  private val varargEventId: VarargEventId
) {

  fun log(project: Project?, filteredEvents:  Map<InlineCompletionLogsContainer.Phase, Collection<EventPair<*>>>) {
    val resultEvents = filteredEvents.mapNotNull { (phase, events) ->
      val eventsWithNewRequestId = replaceRequestId(events)
      val logPhaseObject = InlineCompletionLogs.Session.phases[phase]
      if (logPhaseObject != null) {
        logPhaseObject.with(ObjectEventData(eventsWithNewRequestId.toList()))
      } else {
        logger.error("ObjectEventField is not found for $phase, FUS event may be configured incorrectly!")
        null
      }
    }

    if (resultEvents.isEmpty()) return

    varargEventId.log(project, resultEvents)
  }


  @Suppress("UNCHECKED_CAST")
  private fun replaceRequestId(events: Collection<EventPair<*>>): List<EventPair<*>> {
    val requestIdEvent = events.firstOrNull { it.field.name.contains("request_id") } as? EventPair<Long>
      ?: return events.toList()
    return events.filter { it != requestIdEvent } + listOf(requestIdEvent.field with customRequestId)
  }

  companion object {
    private val logger by lazy { logger<CustomRequestIdLogger>() }

    private val KEY = Key.create<CustomRequestIdLogger>("inline.completion.custom.request.id.logger")

    fun create(editor: Editor, requestId: Long, varargEventId: VarargEventId): CustomRequestIdLogger =
      CustomRequestIdLogger(requestId, varargEventId).also { editor.putUserData(KEY, it) }

    fun get(editor: Editor): CustomRequestIdLogger? = editor.getUserData(KEY)

    fun remove(editor: Editor): CustomRequestIdLogger? = editor.removeUserData(KEY)
  }
}