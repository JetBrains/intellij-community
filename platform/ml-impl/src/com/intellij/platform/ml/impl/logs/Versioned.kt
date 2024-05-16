// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.logs

import com.intellij.openapi.util.Version
import com.intellij.platform.ml.MLModel
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.analysis.SessionAnalyser
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.logs.schema.EventField
import com.intellij.platform.ml.logs.schema.EventPair
import org.jetbrains.annotations.ApiStatus

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
class ModelVersionAnalyser<M, P : Any> : SessionAnalyser.Default<M, P>()
  where M : MLModel<P>,
        M : Versioned {
  companion object {
    private val VERSION = VersionEventField("model_version", null)
  }

  override suspend fun onSessionStarted(callParameters: Environment, sessionEnvironment: Environment, session: Session<P>, mlModel: M): List<EventPair<*>> =
    buildList {
      mlModel.version?.let { VERSION with it }
    }

  override val declaration: List<EventField<*>> = listOf(VERSION)
}
