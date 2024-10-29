// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.tools

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.platform.ml.impl.tools.logs.IntelliJFusEventRegister
import com.jetbrains.ml.MLTask
import com.jetbrains.ml.model.MLModel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun <M : MLModel<P>, P : Any> EventLogGroup.registerMLTaskLogging(
  task: MLTask<M, P>,
  parentDisposable: Disposable,
  eventPrefix: String = task.id,
) {
  val componentRegister = IntelliJFusEventRegister(this)
  val listenerController = componentRegister.registerLogging(task, eventPrefix)
  parentDisposable.whenDisposed { listenerController.remove() }
}
