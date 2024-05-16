// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.lang.Language
import com.intellij.platform.ml.MLModel
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.analysis.SessionAnalyser
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.logs.schema.EventField
import com.intellij.platform.ml.logs.schema.EventPair
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
class ModelLanguageAnalyser<M, P : Any> : SessionAnalyser.Default<M, P>()
  where M : MLModel<P>,
        M : LanguageSpecific {
  private val LANGUAGE = LanguageEventField("model_language", null)

  override suspend fun onSessionStarted(callParameters: Environment, sessionEnvironment: Environment, session: Session<P>, mlModel: M): List<EventPair<*>> {
    return listOf(LANGUAGE with mlModel.language)
  }

  override val declaration: List<EventField<*>> = listOf(LANGUAGE)
}
