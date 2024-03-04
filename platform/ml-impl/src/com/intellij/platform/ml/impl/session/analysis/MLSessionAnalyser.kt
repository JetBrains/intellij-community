// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.PerTierInstance
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.*
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
class MLSessionAnalyser<M : MLModel<P>, P : Any>(
  structureAnalysers: Collection<StructureAnalyser<M, P>>,
  mlModelAnalysers: Collection<MLModelAnalyser<M, P>>,
  private val sessionAnalysisKeyModel: String = DEFAULT_SESSION_KEY_ML_MODEL
) {
  private val groupedAnalyser = JoinedGroupedSessionAnalyser(structureAnalysers, mlModelAnalysers)

  fun analyseSessionStructure(treeRoot: DescribedRootContainer<M, P>): CompletableFuture<AnalysedRootContainer<P>> {
    return groupedAnalyser.analyse(treeRoot).thenApply {
      buildAnalysedSessionTree(treeRoot, it) as AnalysedRootContainer<P>
    }
  }

  /**
   * Static declaration of the features, that are used in the session tree's analysis.
   */
  val structureAnalysisDeclaration: StructureAnalysisDeclaration
    get() = groupedAnalyser.analysisDeclaration.sessionStructureAnalysis

  /**
   * Static declaration of the session's entities, that are not tiers.
   */
  val sessionAnalysisDeclaration: Map<String, Set<FeatureDeclaration<*>>> = mapOf(
    sessionAnalysisKeyModel to groupedAnalyser.analysisDeclaration.mlModelAnalysis
  )

  private fun buildAnalysedSessionTree(tree: DescribedSessionTree<M, P>, analysis: GroupedAnalysis<M, P>): AnalysedSessionTree<P> {
    val treeAnalysisPerInstance: PerTierInstance<AnalysedTierData> = tree.levelData.mainInstances.entries
      .associate { (tierInstance, data) ->
        tierInstance to AnalysedTierData(data.description,
                                         analysis.structureAnalysis[tree]?.get(tierInstance.tier) ?: emptySet())
      }

    val analysedLevel = AnalysedLevel(
      mainInstances = treeAnalysisPerInstance,
      additionalInstances = tree.levelData.additionalInstances,
      callParameters = tree.levelData.callParameters
    )

    return when (tree) {
      is SessionTree.Branching<M, DescribedTierData, P> -> {
        SessionTree.Branching(analysedLevel,
                              tree.children.map { buildAnalysedSessionTree(it, analysis) })
      }
      is SessionTree.Leaf<M, DescribedTierData, P> -> {
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
