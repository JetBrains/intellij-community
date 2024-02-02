// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.impl.model.MLModel

/**
 * An analyzer which is dedicated to give features to the session's model.
 *
 * [SessionAnalyser.analysisDeclaration] returns a set of [FeatureDeclaration] -
 * the features that the model will be described with.
 * [SessionAnalyser.analyse] in this case returns a set of features - the model's analysis.
 */
typealias MLModelAnalyser<M, P> = SessionAnalyser<Set<FeatureDeclaration<*>>, Set<Feature>, M, P>

internal class MLModelAnalysisJoiner<M : MLModel<P>, P : Any> : AnalysisJoiner<Set<FeatureDeclaration<*>>, Set<Feature>, M, P> {
  override fun joinDeclarations(declarations: Iterable<Set<FeatureDeclaration<*>>>): Set<FeatureDeclaration<*>> {
    return declarations.flatten().toSet()
  }

  override fun joinAnalysis(analysis: Iterable<Set<Feature>>): Set<Feature> {
    return analysis.flatten().toSet()
  }
}
