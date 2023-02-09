// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.observable.operation.OperationExecutionId

interface ObservableOperationState {

  val status: ObservableOperationStatus

  val scheduled: Map<OperationExecutionId, Int>

  val started: Map<OperationExecutionId, Int>

  operator fun component1(): ObservableOperationStatus
  operator fun component2(): Map<OperationExecutionId, Int>
  operator fun component3(): Map<OperationExecutionId, Int>
}