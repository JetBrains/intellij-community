// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ml.MLApiPlatform
import com.intellij.platform.ml.MLModel
import com.intellij.platform.ml.MLTask
import com.intellij.platform.ml.MLTaskApproach
import com.intellij.platform.ml.Session
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.impl.logs.ComponentAsFusEventRegister
import com.intellij.platform.ml.logs.AnalysisMethods
import com.intellij.platform.ml.logs.MLSessionLoggingStrategy
import com.intellij.platform.ml.logs.registerMLTaskLogging
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun <M : MLModel<P>, P : Any> EventLogGroup.registerMLTaskLogging(
  eventName: String,
  task: MLTask<P>,
  loggingStrategy: MLSessionLoggingStrategy<P>,
  analysisMethods: AnalysisMethods<M, P> = AnalysisMethods.none(),
  apiPlatform: MLApiPlatform = ReplaceableIJPlatform,
  disposable: Disposable
) {
  val componentRegister = ComponentAsFusEventRegister(this)
  val listenerController = componentRegister.registerMLTaskLogging(eventName, task, loggingStrategy, analysisMethods, apiPlatform)
  Disposer.register(disposable) { listenerController.remove() }
}

@ApiStatus.Internal
suspend fun <P : Any> MLTask<P>.startMLSession(callParameters: Environment, permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
  return MLTaskApproach.startMLSession(this@startMLSession, ReplaceableIJPlatform, callParameters, permanentSessionEnvironment)
}
