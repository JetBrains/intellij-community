// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools.logs

import com.intellij.openapi.util.Version
import com.jetbrains.ml.logs.schema.EventField
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
class ModelVersionAnalyser<M, P : Any> : com.jetbrains.ml.analysis.MLTaskAnalyserTyped<M, P>
  where M : com.jetbrains.ml.model.MLModel<P>,
        M : Versioned {
  companion object {
    private val VERSION = VersionEventField("model_version") { "Version of the ML model" }
  }

  override fun startMLSessionAnalysis(sessionInfo: com.jetbrains.ml.session.MLSessionInfo<M, P>) = object : com.jetbrains.ml.analysis.MLSessionAnalyserTyped<M, P> {
    override suspend fun analyseSession(tree: com.jetbrains.ml.tree.MLTree.ATopNode<M, P>?) = buildList {
      tree?.mlModel?.version?.let { add(VERSION with it) }
    }
  }

  override val declaration: List<EventField<*>> = listOf(VERSION)
}
