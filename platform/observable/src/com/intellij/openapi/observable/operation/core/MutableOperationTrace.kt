// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.observable.operation.OperationExecutionId
import com.intellij.openapi.observable.operation.OperationExecutionStatus
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface MutableOperationTrace : ObservableTaskOperationTrace {

  /**
   * Marks in trace that this operation is guaranteed to be started.
   *
   * @param id is operation's task identification.
   */
  fun traceSchedule(id: OperationExecutionId = OperationExecutionId.NONE)

  /**
   * Marks in trace that this operation will be started immediately.
   *
   * @param id is operation's task identification.
   */
  fun traceStart(id: OperationExecutionId = OperationExecutionId.NONE)

  /**
   * Marks in trace that this operation was finished.
   *
   * @param id is operation's task identification.
   * @param status is an execution result [Success, Cancel, Failure].
   * Cancel has nothing common with detach.
   */
  fun traceFinish(
    id: OperationExecutionId = OperationExecutionId.NONE,
    status: OperationExecutionStatus = OperationExecutionStatus.Success
  )

  /**
   * Removes all tasks with [id]. If all tasks are removed then operation is completed.
   *
   * @param id is operation's task identification.
   */
  fun detach(id: OperationExecutionId = OperationExecutionId.NONE)

  /**
   * Removes all tasks and mark operation as completed.
   */
  fun detachAll()
}