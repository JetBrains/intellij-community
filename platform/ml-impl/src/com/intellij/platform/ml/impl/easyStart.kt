// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.platform.ml.*
import com.intellij.platform.ml.environment.Environment
import com.intellij.platform.ml.impl.logs.ComponentAsFusEventRegister
import com.intellij.platform.ml.logs.AnalysisMethods
import com.intellij.platform.ml.logs.MLSessionLoggingStrategy
import com.intellij.platform.ml.logs.registerMLTaskLogging
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun <M : MLModel<P>, P : Any> EventLogGroup.registerMLTaskLogging(
  eventName: String,
  task: MLTask<P>,
  loggingStrategy: MLSessionLoggingStrategy<P>,
  analysisMethods: AnalysisMethods<M, P> = AnalysisMethods.none(),
  apiPlatform: MLApiPlatform = ReplaceableIJPlatform,
) {
  val componentRegister = ComponentAsFusEventRegister(this)
  val listenerController = componentRegister.registerMLTaskLogging(eventName, task, loggingStrategy, analysisMethods, apiPlatform)
  application.whenDisposed { listenerController.remove() }
}

@ApiStatus.Internal
suspend fun <P : Any> MLTask<P>.startMLSession(callParameters: Environment, permanentSessionEnvironment: Environment): Session.StartOutcome<P> {
  return MLTaskApproach.startMLSession(this@startMLSession, ReplaceableIJPlatform, callParameters, permanentSessionEnvironment)
}
