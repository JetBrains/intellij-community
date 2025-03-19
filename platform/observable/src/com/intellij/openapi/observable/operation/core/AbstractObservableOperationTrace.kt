// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher0
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher2
import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
abstract class AbstractObservableOperationTrace : ObservableTaskOperationTrace {

  private val scheduleTaskMulticaster = SingleEventDispatcher.create<OperationExecutionId>()
  private val startTaskMulticaster = SingleEventDispatcher.create<OperationExecutionId>()
  private val finishTaskMulticaster = SingleEventDispatcher.create2<OperationExecutionId, OperationExecutionStatus>()
  private val detachTaskMulticaster = SingleEventDispatcher.create<OperationExecutionId>()

  override val scheduleTaskObservable: SingleEventDispatcher<OperationExecutionId> = scheduleTaskMulticaster
  override val startTaskObservable: SingleEventDispatcher<OperationExecutionId> = startTaskMulticaster
  override val finishTaskObservable: SingleEventDispatcher2<OperationExecutionId, OperationExecutionStatus> = finishTaskMulticaster
  override val detachTaskObservable: SingleEventDispatcher<OperationExecutionId> = detachTaskMulticaster

  private val scheduleMulticaster = SingleEventDispatcher.create()
  private val startMulticaster = SingleEventDispatcher.create()
  private val finishMulticaster = SingleEventDispatcher.create()

  override val scheduleObservable: SingleEventDispatcher0 = scheduleMulticaster
  override val startObservable: SingleEventDispatcher0 = startMulticaster
  override val finishObservable: SingleEventDispatcher0 = finishMulticaster

  protected fun fireOperationScheduled(): Unit =
    scheduleMulticaster.fireEvent()

  protected fun fireOperationStarted(): Unit =
    startMulticaster.fireEvent()

  protected fun fireOperationFinished(): Unit =
    finishMulticaster.fireEvent()

  protected fun fireTaskScheduled(id: OperationExecutionId): Unit =
    scheduleTaskMulticaster.fireEvent(id)

  protected fun fireTaskStarted(id: OperationExecutionId): Unit =
    startTaskMulticaster.fireEvent(id)

  protected fun fireTaskFinished(id: OperationExecutionId, status: OperationExecutionStatus): Unit =
    finishTaskMulticaster.fireEvent(id, status)

  protected fun fireTaskDetached(id: OperationExecutionId): Unit =
    detachTaskMulticaster.fireEvent(id)
}