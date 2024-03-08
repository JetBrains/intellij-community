// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.openapi.util.Version
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * Something, that has versions.
 */
@ApiStatus.Internal
interface Versioned {
  val version: Version?
}

/**
 * Adds model's version to the ML logs.
 */
@ApiStatus.Internal
class ModelVersionAnalyser<M, P : Any> : MLModelAnalyser<M, P>
  where M : MLModel<P>,
        M : Versioned {
  companion object {
    val VERSION = FeatureDeclaration.version("version").nullable()
  }

  override val analysisDeclaration = setOf(VERSION)

  override fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): CompletableFuture<Set<Feature>> = CompletableFuture.completedFuture(
    setOf(VERSION with sessionTreeRoot.rootData.version)
  )
}
