// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
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

  override val scheduleTaskObservable: SingleEventDispatcher.Observable1<OperationExecutionId> = scheduleTaskMulticaster
  override val startTaskObservable: SingleEventDispatcher.Observable1<OperationExecutionId> = startTaskMulticaster
  override val finishTaskObservable: SingleEventDispatcher.Observable2<OperationExecutionId, OperationExecutionStatus> = finishTaskMulticaster
  override val detachTaskObservable: SingleEventDispatcher.Observable1<OperationExecutionId> = detachTaskMulticaster

  private val scheduleMulticaster = SingleEventDispatcher.create()
  private val startMulticaster = SingleEventDispatcher.create()
  private val finishMulticaster = SingleEventDispatcher.create()

  override val scheduleObservable: SingleEventDispatcher.Observable = scheduleMulticaster
  override val startObservable: SingleEventDispatcher.Observable = startMulticaster
  override val finishObservable: SingleEventDispatcher.Observable = finishMulticaster

  protected fun fireOperationScheduled() =
    scheduleMulticaster.fireEvent()

  protected fun fireOperationStarted() =
    startMulticaster.fireEvent()

  protected fun fireOperationFinished() =
    finishMulticaster.fireEvent()

  protected fun fireTaskScheduled(id: OperationExecutionId) =
    scheduleTaskMulticaster.fireEvent(id)

  protected fun fireTaskStarted(id: OperationExecutionId) =
    startTaskMulticaster.fireEvent(id)

  protected fun fireTaskFinished(id: OperationExecutionId, status: OperationExecutionStatus) =
    finishTaskMulticaster.fireEvent(id, status)

  protected fun fireTaskDetached(id: OperationExecutionId) =
    detachTaskMulticaster.fireEvent(id)
}