// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * An interface for classes, that are analyzing the ML session after it was finished.
 *
 * Analysis is indefinitely long process (which is emphasized by the fact [analyse] returns a [CompletableFuture]).
 *
 * @param D Type of the analysis' declaration
 * @param A Type of the analysis itself
 * @param M Type of the model that has been utilized during the ML session
 * @param P Type of the session's prediction
 */
@ApiStatus.Internal
interface SessionAnalyser<D, A, M : MLModel<P>, P : Any> {
  /**
   * Contains all features' declarations - [com.intellij.platform.ml.FeatureDeclaration],
   * that are then used in the analysis as [analyse]'s return value.
   */
  val analysisDeclaration: D

  /**
   * Performs session tree's analysis. The analysis is performed asynchronously,
   * so the function is not required to return the final result, but only a [CompletableFuture]
   * that will be fulfilled when the analysis is finished.
   */
  fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): CompletableFuture<A>
}

/**
 * Gathers analyses from different [SessionAnalyser]s to one.
 */
internal class JoinedSessionAnalyser<D, A, M : MLModel<P>, P : Any>(
  private val baseAnalysers: Collection<SessionAnalyser<D, A, M, P>>,
  private val joiner: AnalysisJoiner<D, A, M, P>
) : SessionAnalyser<D, A, M, P> {
  override fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): CompletableFuture<A> {
    val scatteredAnalysis = mutableListOf<CompletableFuture<A>>()
    for (sessionAnalyser in baseAnalysers) {
      scatteredAnalysis.add(sessionAnalyser.analyse(sessionTreeRoot))
    }

    val joinedAnalysis = CompletableFuture<A>()
    val eachScatteredAnalysisFinished = CompletableFuture.allOf(*scatteredAnalysis.toTypedArray())

    eachScatteredAnalysisFinished.thenRun {
      val completeAnalysis = scatteredAnalysis.mapNotNull { it.get() }
      joinedAnalysis.complete(joiner.joinAnalysis(completeAnalysis))
    }

    return joinedAnalysis
  }

  override val analysisDeclaration: D
    get() = joiner.joinDeclarations(baseAnalysers.map { it.analysisDeclaration })
}

internal interface AnalysisJoiner<D, A, M : MLModel<P>, P : Any> {
  fun joinDeclarations(declarations: Iterable<D>): D

  fun joinAnalysis(analysis: Iterable<A>): A
}
