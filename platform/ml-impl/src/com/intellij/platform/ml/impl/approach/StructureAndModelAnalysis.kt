// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.approach

import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.PerTierInstance
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.*
import com.intellij.platform.ml.impl.session.analysis.MLModelAnalyser
import com.intellij.platform.ml.impl.session.analysis.StructureAnalyser
import com.intellij.platform.ml.impl.session.analysis.StructureAnalysisDeclaration
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * An analysis method, that asynchronously analyzes session structure after
 * it finished, and the ML model that has been used during the session.
 *
 * @param structureAnalysers Analyzing session's structure - main tier instances.
 * @param mlModelAnalysers Analyzing the ML model which was producing predictions during the session.
 * @param sessionAnalysisKeyModel Key that will be used in logs, to write ML model's features into.
 */
@ApiStatus.Internal
class StructureAndModelAnalysis<M : MLModel<P>, P : Any>(
  structureAnalysers: Collection<StructureAnalyser<M, P>>,
  mlModelAnalysers: Collection<MLModelAnalyser<M, P>>,
  private val sessionAnalysisKeyModel: String = DEFAULT_SESSION_KEY_ML_MODEL
) : AnalysisMethod<M, P> {
  private val groupedAnalyser = JoinedGroupedSessionAnalyser(structureAnalysers, mlModelAnalysers)

  override val structureAnalysisDeclaration: StructureAnalysisDeclaration
    get() = groupedAnalyser.analysisDeclaration.structureAnalysis

  override val sessionAnalysisDeclaration: Map<String, Set<FeatureDeclaration<*>>> = mapOf(
    sessionAnalysisKeyModel to groupedAnalyser.analysisDeclaration.mlModelAnalysis
  )

  override fun analyseTree(treeRoot: DescribedRootContainer<M, P>): CompletableFuture<AnalysedRootContainer<P>> {
    return groupedAnalyser.analyse(treeRoot).thenApply {
      buildAnalysedSessionTree(treeRoot, it) as AnalysedRootContainer<P>
    }
  }

  private fun buildAnalysedSessionTree(tree: DescribedSessionTree<M, P>, analysis: GroupedAnalysis<M, P>): AnalysedSessionTree<P> {
    val treeAnalysisPerInstance: PerTierInstance<AnalysedTierData> = tree.level.main.entries
      .associate { (tierInstance, data) ->
        tierInstance to AnalysedTierData(data.description,
                                         analysis.structureAnalysis[tree]?.get(tierInstance.tier) ?: emptySet())
      }

    val analysedLevel = Level(
      main = treeAnalysisPerInstance,
      additional = tree.level.additional
    )

    return when (tree) {
      is SessionTree.Branching<M, DescribedLevel, P> -> {
        SessionTree.Branching(analysedLevel,
                              tree.children.map { buildAnalysedSessionTree(it, analysis) })
      }
      is SessionTree.Leaf<M, DescribedLevel, P> -> {
        SessionTree.Leaf(analysedLevel, tree.prediction)
      }
      is SessionTree.ComplexRoot -> {
        SessionTree.ComplexRoot(mapOf(sessionAnalysisKeyModel to analysis.mlModelAnalysis),
                                analysedLevel,
                                tree.children.map { buildAnalysedSessionTree(it, analysis) }
        )
      }
      is SessionTree.SolitaryLeaf -> {
        SessionTree.SolitaryLeaf(mapOf(sessionAnalysisKeyModel to analysis.mlModelAnalysis),
                                 analysedLevel,
                                 tree.prediction
        )
      }
    }
  }

  companion object {
    const val DEFAULT_SESSION_KEY_ML_MODEL: String = "ml_model"
  }
}
