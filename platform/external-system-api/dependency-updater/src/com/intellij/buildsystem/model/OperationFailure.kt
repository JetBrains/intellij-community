package com.intellij.buildsystem.model

/**
 * Represents a failed operation, and contains info on the type of failed operation,
 * the [error] that occurred, and the [item] the error occurred for.
 *
 * The type parameter [T] is the type of the item that failed.
 */
data class OperationFailure<T : OperationItem>(
  val operationType: OperationType,
  val item: T,
  val error: Throwable
)
