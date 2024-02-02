// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.PerTier
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.DescribedSessionTree
import com.intellij.platform.ml.mergePerTier

typealias StructureAnalysis<M, P> = Map<DescribedSessionTree<M, P>, PerTier<Set<Feature>>>

typealias StructureAnalysisDeclaration = PerTier<Set<FeatureDeclaration<*>>>

/**
 * An analyzer that gives analytical features to the session tree's nodes.
 *
 * [SessionAnalyser.analysisDeclaration] returns a set of [FeatureDeclaration] per each analyzed tier.
 * [SessionAnalyser.analyse] returns a mapping from each analyzed tree's node to sets of features.
 *
 * For example, if you want to give some analysis to the session tree's root, then you will return
 * mapOf(sessionTreeRoot to setOf(...)).
 *
 * @see com.intellij.platform.ml.impl.session.SessionTree.Visitor to learn how you could walk the tree's nodes
 * @see com.intellij.platform.ml.impl.session.SessionTree.LevelVisitor to see learn how you could session's levels.
 */
typealias StructureAnalyser<M, P> = SessionAnalyser<StructureAnalysisDeclaration, StructureAnalysis<M, P>, M, P>

internal class SessionStructureAnalysisJoiner<M : MLModel<P>, P : Any> : AnalysisJoiner<StructureAnalysisDeclaration, StructureAnalysis<M, P>, M, P> {
  override fun joinAnalysis(analysis: Iterable<StructureAnalysis<M, P>>): StructureAnalysis<M, P> {
    return analysis
      .flatMap { it.entries }
      .groupBy({ it.key }, { it.value })
      .mapValues { it.value.mergePerTier { mutableSetOf() } }
  }

  override fun joinDeclarations(declarations: Iterable<StructureAnalysisDeclaration>): StructureAnalysisDeclaration {
    return declarations.mergePerTier { mutableSetOf() }
  }
}
