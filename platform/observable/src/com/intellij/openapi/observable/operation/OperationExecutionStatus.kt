// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus

sealed interface OperationExecutionStatus {
  @ApiStatus.Internal
  object Cancel : OperationExecutionStatus {
    override fun toString(): String = "Cancel"
  }

  @ApiStatus.Internal
  object Success : OperationExecutionStatus {
    override fun toString(): String = "Success"
  }

  @ApiStatus.Internal
  class Failure private constructor(
    val message: String?,
    val cause: Throwable?
  ) : OperationExecutionStatus {

    constructor() : this(null, null)
    constructor(cause: Throwable) : this(cause.message, cause)
    constructor(message: String) : this(message, null)

    override fun toString(): String = "Failure" + message?.let { "($it)" }
  }

  @ApiStatus.Internal
  companion object {
    val KEY: DataKey<OperationExecutionStatus> = DataKey.create("com.intellij.openapi.observable.dispatcher.operation.TaskExecutionStatus")
  }
}