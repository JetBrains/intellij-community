// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation

import org.jetbrains.annotations.ApiStatus

/**
 * Identification of marking execution and context holder.
 */
@ApiStatus.NonExtendable
interface OperationExecutionId {

  /**
   * Execution context allows to forward data through execution events.
   */
  val executionContext: OperationExecutionContext

  companion object {

    val NONE = createId("NONE")

    fun createId(
      debugName: String? = null,
      configure: OperationExecutionContext.Builder.() -> Unit = {}
    ) = createId(debugName, OperationExecutionContext.create(configure))

    fun createId(
      debugName: String? = null,
      context: OperationExecutionContext
    ): OperationExecutionId {
      return object : OperationExecutionId {
        override val executionContext = context
        override fun toString() = debugName ?: "UNKNOWN"
      }
    }
  }
}
