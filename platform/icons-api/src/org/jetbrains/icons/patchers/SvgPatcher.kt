// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.patchers

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi

@ExperimentalIconsApi
@Serializable
public class SvgPatcher(
  val operations: List<SvgPatchOperation>,
  val filteredOperations: List<SvgPathFilteredOperations>
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SvgPatcher

    if (operations != other.operations) return false
    if (filteredOperations != other.filteredOperations) return false

    return true
  }

  override fun hashCode(): Int {
    var result = operations.hashCode()
    result = 31 * result + filteredOperations.hashCode()
    return result
  }

  override fun toString(): String {
    return "SvgPatcher(operations=$operations, filteredOperations=$filteredOperations)"
  }

}

@ExperimentalIconsApi
infix fun SvgPatcher?.combineWith(other: SvgPatcher?): SvgPatcher? {
  if (this == null && other == null) return null
  if (this == null) return other!!
  if (other == null) return this
  return SvgPatcher(operations + other.operations, filteredOperations + other.filteredOperations)
}

@ExperimentalIconsApi
@Serializable
public class SvgPathFilteredOperations(
  val path: String,
  val operations: List<SvgPatchOperation>
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SvgPathFilteredOperations

    if (path != other.path) return false
    if (operations != other.operations) return false

    return true
  }

  override fun hashCode(): Int {
    var result = path.hashCode()
    result = 31 * result + operations.hashCode()
    return result
  }

  override fun toString(): String {
    return "SvgPathFilteredOperations(path='$path', operations=$operations)"
  }

}

@ExperimentalIconsApi
@Serializable
public class SvgPatchOperation(
  val attributeName: String,
  val value: String?,
  val conditional: Boolean,
  val negatedCondition: Boolean,
  val expectedValue: String?,
  val operation: Operation
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SvgPatchOperation

    if (conditional != other.conditional) return false
    if (negatedCondition != other.negatedCondition) return false
    if (attributeName != other.attributeName) return false
    if (value != other.value) return false
    if (expectedValue != other.expectedValue) return false
    if (operation != other.operation) return false

    return true
  }

  override fun hashCode(): Int {
    var result = conditional.hashCode()
    result = 31 * result + negatedCondition.hashCode()
    result = 31 * result + attributeName.hashCode()
    result = 31 * result + (value.hashCode() ?: 0)
    result = 31 * result + (expectedValue.hashCode() ?: 0)
    result = 31 * result + operation.hashCode()
    return result
  }

  override fun toString(): String {
    return "SvgPatchOperation(attributeName='$attributeName', value=$value, conditional=$conditional, negatedCondition=$negatedCondition, expectedValue=$expectedValue, operation=$operation)"
  }

  @Serializable
  enum class Operation {
    Add,
    Replace,
    Remove,
    Set
  }

}
