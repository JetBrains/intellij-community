// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events

import com.intellij.build.eventBuilders.OutputReferenceEventBuilder
import org.jetbrains.annotations.ApiStatus.NonExtendable

/**
 * Attaches the already reported output to the [StartEvent] node with the [startId] id.
 *
 * This event doesn't produce any output.
 * It allows reusing the output of the main build process [StartBuildEvent] in subtasks or sub-processes [StartEvent],
 * instead of duplicating it.
 *
 * This event isn't a build tree node: it has no id, parent id, time, message, hint, or description.
 *
 * **Note:** supported only when the first [com.intellij.build.events.StartBuildEvent] have a configured instance of
 * [com.intellij.build.BuildViewSettingsProvider] and [com.intellij.build.BuildViewSettingsProvider.isSingleBuildConsoleView] is true.
 */
@NonExtendable
interface OutputReferenceEvent : BuildEvent {

  /**
   * [StartEvent.id] of the node to attach the referenced output to.
   */
  val startId: Any

  /**
   * [OutputBuildEvent.id]'s that are referenced by this event.
   */
  val outputIds: List<Any>

  companion object {

    @JvmStatic
    fun builder(startId: Any, outputIds: List<Any>): OutputReferenceEventBuilder =
      BuildEvents.getInstance().outputReference(startId, outputIds)
  }
}