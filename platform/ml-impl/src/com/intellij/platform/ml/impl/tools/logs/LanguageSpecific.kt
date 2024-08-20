// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools.logs

import com.intellij.lang.Language
import com.jetbrains.ml.logs.schema.EventField
import org.jetbrains.annotations.ApiStatus

/**
 * Something, that is dedicated for one language only.
 */
@ApiStatus.Internal
interface LanguageSpecific {
  val language: Language
}

/**
 * The analyzer, that adds information about ML model's language to logs.
 */
@ApiStatus.Internal
class ModelLanguageAnalyser<M, P : Any> : com.jetbrains.ml.analysis.MLTaskAnalyserTyped<M, P>
  where M : com.jetbrains.ml.model.MLModel<P>,
        M : LanguageSpecific {
  private val LANGUAGE = LanguageEventField("model_language") { "The programming language the ML model is trained for" }

  override fun startMLSessionAnalysis(sessionInfo: com.jetbrains.ml.session.MLSessionInfo<M, P>) = object : com.jetbrains.ml.analysis.MLSessionAnalyserTyped<M, P> {
    override suspend fun analyseSession(tree: com.jetbrains.ml.tree.MLTree.ATopNode<M, P>?) = buildList {
      tree?.mlModel?.let { add(LANGUAGE with it.language) }
    }
  }

  override val declaration: List<EventField<*>> = listOf(LANGUAGE)
}
