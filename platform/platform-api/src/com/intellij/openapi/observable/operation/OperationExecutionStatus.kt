// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation

import com.intellij.openapi.actionSystem.DataKey

sealed interface OperationExecutionStatus {

  object Cancel : OperationExecutionStatus {
    override fun toString(): String = "Cancel"
  }

  object Success : OperationExecutionStatus {
    override fun toString(): String = "Success"
  }

  class Failure private constructor(
    val message: String?,
    val cause: Throwable?
  ) : OperationExecutionStatus {

    constructor() : this(null, null)
    constructor(cause: Throwable) : this(cause.message, cause)
    constructor(message: String) : this(message, null)

    override fun toString(): String = "Failure" + message?.let { "($it)" }
  }

  companion object {
    val KEY = DataKey.create<OperationExecutionStatus>("com.intellij.openapi.observable.dispatcher.operation.TaskExecutionStatus")
  }
}