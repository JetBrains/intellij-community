package org.jetbrains.jewel.markdown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Represents a size value for an image dimension (width or height).
 *
 * Currently only pixel values ([Pixels]) are supported.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface DimensionSize {
    /** A loaded image should be exactly [value] pixels in the specified dimension. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @JvmInline
    public value class Pixels(public val value: Int) : DimensionSize {
        init {
            require(value >= 0) { "Value cannot be negative." }
        }

        override fun toString(): String = "${value}px"
    }

    // TODO[JEWEL-1333]: Explore percentage sizing further since the first iteration resized the image in relation
    //  to the percentage instead of making the image fill the required percentage of the available Canvas space.
}

internal fun String.parseDimensionSize(): DimensionSize? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null

    val number = trimmed.takeWhile { it.isDigit() }
    val convertedNumber = number.toIntOrNull() ?: return null
    val normalizedUnit = trimmed.substringAfter(number).trim().trimEnd(';').lowercase()

    return when (normalizedUnit) {
        "" -> DimensionSize.Pixels(convertedNumber)
        "px" -> DimensionSize.Pixels(convertedNumber)
        else -> null
    }
}
