// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.PerTier
import com.intellij.platform.ml.impl.LevelSignature
import com.intellij.platform.ml.impl.MLTaskApproach.Companion.startMLSession
import com.intellij.platform.ml.impl.apiPlatform.MLApiPlatform
import com.intellij.platform.ml.impl.session.AnalysedRootContainer
import com.intellij.platform.ml.impl.session.AnalysedTierScheme
import com.intellij.platform.ml.impl.session.DescribedTierScheme
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLSessionLoggingStrategy<P : Any> {
  /**
   * Creates a logger to log the described ML session.
   *
   * @param sessionAnalysisDeclaration The additional fields for the ML tasks, taken from the various [com.intellij.platform.ml.impl.session.analysis.SessionAnalyser].
   * @param sessionStructureAnalysisDeclaration The structure of the session tiers' description features. The features are taken from [com.intellij.platform.ml.TierDescriptor],
   * and the analysis from [com.intellij.platform.ml.impl.session.analysis.StructureAnalyser].
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
  /**
   * Takes all the collected information during the ML session, and logs it.
   *
   * @param apiPlatform The platform, that is used in this session
   * @param permanentSessionEnvironment The tier instances, located on the top ("permanent") level of the ML Task
   * @param permanentCallParameters The call parameters from [com.intellij.platform.ml.impl.MLTaskApproach.Companion.startMLSession] will be passed here
   * @param session The additional session fields, collected by the [com.intellij.platform.ml.impl.session.analysis.SessionAnalyser]s.
   * @param structure The ML session's structure that contains tiers' descriptions, as well as analysis.
   */
  fun logSession(apiPlatform: MLApiPlatform, permanentSessionEnvironment: Environment, permanentCallParameters: Environment,
                 session: List<EventPair<*>>, structure: AnalysedRootContainer<P>?)
}
