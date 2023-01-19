// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher2
import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import org.jetbrains.annotations.ApiStatus

/**
 * Defines observation API for observable process with tasks.
 */
@ApiStatus.NonExtendable
interface ObservableTaskOperationTrace: ObservableOperationTrace {

  val state: ObservableOperationState

  val scheduleTaskObservable: SingleEventDispatcher<OperationExecutionId>

  val startTaskObservable: SingleEventDispatcher<OperationExecutionId>

  val finishTaskObservable: SingleEventDispatcher2<OperationExecutionId, OperationExecutionStatus>

  val detachTaskObservable: SingleEventDispatcher<OperationExecutionId>
}