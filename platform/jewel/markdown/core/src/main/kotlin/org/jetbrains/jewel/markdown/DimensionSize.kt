package org.jetbrains.jewel.markdown

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/** Represents a size value for an image dimension (width or height). Can be either a pixel value or a percentage. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface DimensionSize {
    /** A loaded image should be exactly [value] pixels in the specified dimension. */
    @JvmInline
    public value class Pixels(public val value: Int) : DimensionSize {
        override fun toString(): String = "${value}px"
    }

    /** A loaded image should be [value]% of the specified dimension of the loaded image. */
    @JvmInline
    public value class Percent(public val value: Int) : DimensionSize {
        override fun toString(): String = "$value%"
    }
}
