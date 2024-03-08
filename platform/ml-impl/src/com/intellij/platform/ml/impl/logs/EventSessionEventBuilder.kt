// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectDescription
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.platform.ml.impl.MLTaskApproach
import com.intellij.platform.ml.impl.session.AnalysedRootContainer
import com.intellij.platform.ml.impl.session.AnalysedSessionTree
import org.jetbrains.annotations.ApiStatus

/**
 * Represents FUS event fields of a session's subtree.
 */
@ApiStatus.Internal
abstract class SessionFields<P : Any> : ObjectDescription() {
  fun buildObjectEventData(sessionStructure: AnalysedSessionTree<P>) = ObjectEventData(buildEventPairs(sessionStructure))

  abstract fun buildEventPairs(sessionStructure: AnalysedSessionTree<P>): List<EventPair<*>>
}

/**
 * Represents a logging scheme for the FUS event.
 *
 * @param P The type of the ML task's prediction
 */
@ApiStatus.Internal
interface EventSessionEventBuilder<P : Any> {
  /**
   * Configuration of a [EventSessionEventBuilder], that builds it when accepts approach's declaration.
   */
  interface EventScheme<P : Any> {
    fun createEventBuilder(approachDeclaration: MLTaskApproach.SessionDeclaration): EventSessionEventBuilder<P>
  }

  /**
   * Builds declaration of all features, that will be logged for the session tiers' description and analysis.
   */
  fun buildSessionFields(): SessionFields<P>

  /**
   * Builds a concrete log record, that contains fields that were built by [buildSessionFields].
   *
   * @param sessionStructure A session tree that been already analyzed and is ready to be logged.
   * @param sessionFields Session fields that were built by [buildSessionFields] earlier.
   */
  fun buildRecord(sessionStructure: AnalysedRootContainer<P>, sessionFields: SessionFields<P>): Array<EventPair<*>>
}
