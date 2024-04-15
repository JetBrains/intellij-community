// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.ml.PerTier
import com.intellij.platform.ml.impl.LevelSignature
import com.intellij.platform.ml.impl.session.AnalysedRootContainer
import com.intellij.platform.ml.impl.session.AnalysedTierScheme
import com.intellij.platform.ml.impl.session.DescribedTierScheme
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLSessionScheme<P : Any> {
  /**
   * @param eventLogGroup Will contain events logged by the returned logger
   * @param eventPrefix A unique prefix for the logger's events' names
   */
  fun configureLogger(sessionAnalysisDeclaration: List<EventField<*>>,
                      sessionStructureAnalysisDeclaration: List<AnalysedLevelScheme>,
                      eventLogGroup: EventLogGroup,
                      eventPrefix: String): MLSessionLogger<P>
}

typealias AnalysedLevelScheme = LevelSignature<PerTier<AnalysedTierScheme>, PerTier<DescribedTierScheme>>

@ApiStatus.Internal
interface MLSessionLogger<P : Any> {
  fun logSession(session: List<EventPair<*>>, structure: AnalysedRootContainer<P>?)
}
