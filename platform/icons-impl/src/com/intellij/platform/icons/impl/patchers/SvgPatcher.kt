// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.patchers

import com.intellij.platform.icons.patchers.SvgPatcher
import kotlinx.serialization.Serializable

@Serializable
class DefaultSvgPatcher(
    val operations: List<SvgPatchOperation>,
    val filteredOperations: List<SvgPathFilteredOperations>,
) : SvgPatcher {
    override fun combineWith(other: SvgPatcher?): SvgPatcher {
        if (other !is DefaultSvgPatcher) return this
        return DefaultSvgPatcher(operations + other.operations, filteredOperations + other.filteredOperations)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultSvgPatcher

        if (operations != other.operations) return false
        if (filteredOperations != other.filteredOperations) return false

        return true
    }

    override fun hashCode(): Int {
        var result = operations.hashCode()
        result = 31 * result + filteredOperations.hashCode()
        return result
    }

    override fun toString(): String = "SvgPatcher(operations=$operations, filteredOperations=$filteredOperations)"
}

@Serializable
class SvgPathFilteredOperations(val path: String, val operations: List<SvgPatchOperation>) {
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

    override fun toString(): String = "SvgPathFilteredOperations(path='$path', operations=$operations)"
}

@Serializable
class SvgPatchOperation(
    val attributeName: String,
    val value: String?,
    val conditional: Boolean,
    val negatedCondition: Boolean,
    val expectedValue: String?,
    val operation: Operation,
) {
    @Serializable
    enum class Operation {
        Add,
        Replace,
        Remove,
        Set,
    }

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
        result = 31 * result + (value?.hashCode() ?: 0)
        result = 31 * result + (expectedValue?.hashCode() ?: 0)
        result = 31 * result + operation.hashCode()
        return result
    }

    override fun toString(): String =
        "SvgPatchOperation(attributeName='$attributeName', value=$value, conditional=$conditional, negatedCondition=$negatedCondition, expectedValue=$expectedValue, operation=$operation)"
}
