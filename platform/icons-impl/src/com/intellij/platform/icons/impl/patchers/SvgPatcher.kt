// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.patchers

import com.intellij.platform.icons.patchers.SvgPatcher
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

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

    /**
     * Whether [actualValue], the attribute's current value, satisfies this operation's condition.
     *
     * A plain color compares as a color, because SVG lets one color be written several ways and which one a document
     * uses is an authoring accident: `#6C707E` and `#6c707e` are the same color, and so are `#fff` and `#ffffff`.
     * Everything else compares exactly — an `id`, and equally the fragment in a `fill="url(#Gradient)"` paint
     * reference, is case-sensitive, so folding case there would match a different paint server.
     *
     * Condition evaluation lives here rather than in each renderer so that every frontend resolves the same operation
     * the same way; a renderer that compared differently would draw the same icon differently.
     */
    @ApiStatus.Internal
    fun matches(actualValue: String?): Boolean =
        if (attributeName in COLOR_ATTRIBUTES && isPlainColor(expectedValue) && isPlainColor(actualValue)) {
            sameColor(actualValue, expectedValue)
        } else {
            actualValue == expectedValue
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

/**
 * The attributes whose values are colors, and so compare case-insensitively.
 *
 * Top-level rather than a companion: this class is `@Serializable`, and declaring a companion here would displace the
 * one the serialization plugin generates, taking `SvgPatchOperation.serializer()` out of the public API with it.
 */
private val COLOR_ATTRIBUTES =
    setOf("fill", "stroke", "color", "solid-color", "stop-color", "flood-color", "lighting-color")

/** A hex triplet, with or without alpha. */
private val HEX_COLOR = Regex("#[0-9a-fA-F]{3,8}")

/**
 * A bare word, hyphens allowed: a color keyword such as `white`, `none`, `transparent` or `context-fill`. Never a
 * function like `url(...)`, which is the case-sensitive form this must not swallow.
 */
private val COLOR_KEYWORD = Regex("[a-zA-Z]+(-[a-zA-Z]+)*")

/**
 * Whether [value] is a plain color literal, as opposed to a paint reference such as `url(#Gradient)` whose fragment id
 * is case-sensitive.
 */
private fun isPlainColor(value: String?): Boolean =
    value != null && (HEX_COLOR.matches(value) || COLOR_KEYWORD.matches(value))

/**
 * Whether two plain color literals denote the same color.
 *
 * Hex literals compare canonically, so a shorthand names the same color as the form it stands for. Any alpha the
 * literal carries is not part of which color it names, because a substitution replaces the opacity of what it matched
 * along with the color itself. A keyword, and a hex literal of a length that names no color, compares
 * case-insensitively as text.
 */
private fun sameColor(actualValue: String?, expectedValue: String?): Boolean {
    val actual = actualValue?.toRgbHex()
    val expected = expectedValue?.toRgbHex()
    return if (actual != null && expected != null) {
        actual == expected
    } else {
        actualValue.equals(expectedValue, ignoreCase = true)
    }
}

/**
 * The lowercase `#rrggbb` this hex literal names, or `null` when it names no color.
 *
 * A color carries 3, 4, 6 or 8 significant digits — the lengths `ColorHexUtil` accepts — with the two shorthands
 * standing for doubled pairs. There is no valid five- or seven-digit form.
 */
private fun String.toRgbHex(): String? {
    if (!startsWith('#')) return null

    val digits = drop(1).lowercase()
    return when (digits.length) {
        3, 4 -> "#" + digits.take(3).map { "$it$it" }.joinToString(separator = "")
        6, 8 -> "#" + digits.take(6)
        else -> null
    }
}
