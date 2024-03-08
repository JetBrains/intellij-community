// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * The session's assembled analysis declaration.
 */
@ApiStatus.Internal
data class GroupedAnalysisDeclaration(
  val sessionStructureAnalysis: StructureAnalysisDeclaration,
  val mlModelAnalysis: Set<FeatureDeclaration<*>>
)

/**
 * The session's assembled analysis itself.
 */
@ApiStatus.Internal
data class GroupedAnalysis<M : MLModel<P>, P : Any>(
  val structureAnalysis: StructureAnalysis<M, P>,
  val mlModelAnalysis: Set<Feature>
)

/**
 * Analyzes both structure and ML model.
 */
@ApiStatus.Internal
class JoinedGroupedSessionAnalyser<M : MLModel<P>, P : Any>(
  private val structureAnalysers: Collection<StructureAnalyser<M, P>>,
  private val mlModelAnalysers: Collection<MLModelAnalyser<M, P>>,
) : SessionAnalyser<GroupedAnalysisDeclaration, GroupedAnalysis<M, P>, M, P> {
  override val analysisDeclaration = GroupedAnalysisDeclaration(
    sessionStructureAnalysis = SessionStructureAnalysisJoiner<M, P>().joinDeclarations(structureAnalysers.map { it.analysisDeclaration }),
    mlModelAnalysis = MLModelAnalysisJoiner<M, P>().joinDeclarations(mlModelAnalysers.map { it.analysisDeclaration })
  )

  override fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): CompletableFuture<GroupedAnalysis<M, P>> {
    val joinedStructureAnalyser = JoinedSessionAnalyser(
      structureAnalysers, SessionStructureAnalysisJoiner()
    )
    val joinedMLModelAnalyser = JoinedSessionAnalyser(
      mlModelAnalysers, MLModelAnalysisJoiner()
    )
    val structureAnalysis = joinedStructureAnalyser.analyse(sessionTreeRoot)
    val mlModelAnalysis = joinedMLModelAnalyser.analyse(sessionTreeRoot)

    val futureGroupAnalysis = CompletableFuture.allOf(structureAnalysis, mlModelAnalysis)
    val completeGroupAnalysis = CompletableFuture<GroupedAnalysis<M, P>>()

    futureGroupAnalysis.thenRun {
      completeGroupAnalysis.complete(GroupedAnalysis(structureAnalysis.get(), mlModelAnalysis.get()))
    }

    return completeGroupAnalysis
  }
}
