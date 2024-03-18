// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.approach

import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.PerTier
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.AnalysedRootContainer
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * Represents the method, that is utilized for the [LogDrivenModelInference]'s analysis.
 */
@ApiStatus.Internal
interface AnalysisMethod<M : MLModel<P>, P : Any> {
  /**
   * Static declaration of the features, that are used in the session tree's analysis.
   */
  val structureAnalysisDeclaration: PerTier<Set<FeatureDeclaration<*>>>

  /**
   * Static declaration of the session's entities, that are not tiers.
   */
  val sessionAnalysisDeclaration: Map<String, Set<FeatureDeclaration<*>>>

  /**
   * Perform the completed session's analysis.
   */
  fun analyseTree(treeRoot: DescribedRootContainer<M, P>): CompletableFuture<AnalysedRootContainer<P>>
}
