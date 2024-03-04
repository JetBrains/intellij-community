// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.lang.Language
import com.intellij.platform.ml.Feature
import com.intellij.platform.ml.FeatureDeclaration
import com.intellij.platform.ml.impl.model.MLModel
import com.intellij.platform.ml.impl.session.DescribedRootContainer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

/**
 * Something, that is dedicated for one language only.
 */
@ApiStatus.Internal
interface LanguageSpecific {
  val languageId: String
}

/**
 * The analyzer, that adds information about ML model's language to logs.
 */
@ApiStatus.Internal
class ModelLanguageAnalyser<M, P : Any> : MLModelAnalyser<M, P>
  where M : MLModel<P>,
        M : LanguageSpecific {

  private val LANGUAGE_ID = FeatureDeclaration.categorical("language_id", Language.getRegisteredLanguages().map { it.id }.toSet())

  override val analysisDeclaration = setOf(LANGUAGE_ID)

  override fun analyse(sessionTreeRoot: DescribedRootContainer<M, P>): CompletableFuture<Set<Feature>> = CompletableFuture.completedFuture(
    setOf(LANGUAGE_ID with sessionTreeRoot.rootData.languageId)
  )
}
