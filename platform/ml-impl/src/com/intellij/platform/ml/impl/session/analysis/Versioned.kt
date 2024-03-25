// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.util.Version
import com.intellij.platform.ml.Environment
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.impl.logs.VersionField
import com.intellij.platform.ml.impl.model.MLModel
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
    private val VERSION = VersionField("model_version")
  }

  override suspend fun onSessionStarted(callParameters: Environment, sessionEnvironment: Environment, session: Session<P>, mlModel: M): List<EventPair<*>> {
    return listOf(VERSION with mlModel.version)
  }

  override val declaration: List<EventField<*>> = listOf(VERSION)
}
