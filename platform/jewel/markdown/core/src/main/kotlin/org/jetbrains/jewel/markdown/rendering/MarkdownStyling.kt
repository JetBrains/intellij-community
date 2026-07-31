// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.rendering

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.BottomCenter
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.BottomEnd
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.BottomStart
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.Hide
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.TopCenter
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.TopEnd
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.TopStart

/**
 * Holds all styling configuration for rendering Markdown content, grouping per-block-type styling classes for
 * paragraphs, headings, block quotes, code blocks, lists, images, thematic breaks, and HTML blocks.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class MarkdownStyling(
    /**
     * The vertical spacing applied between sibling Markdown block elements, both at the top level and within container
     * blocks such as block quotes.
     */
    public val blockVerticalSpacing: Dp,
    /** Styling for paragraph blocks. */
    public val paragraph: Paragraph,
    /** Styling for heading blocks (H1–H6). */
    public val heading: Heading,
    /** Styling for block quote elements. */
    public val blockQuote: BlockQuote,
    /** Styling for code blocks (indented and fenced). */
    public val code: Code,
    /** Styling for ordered and unordered list blocks. */
    public val list: List,
    /** Styling for image elements. */
    public val image: Image,
    /** Styling for thematic break (horizontal rule) elements. */
    public val thematicBreak: ThematicBreak,
    /** Styling for HTML block elements. */
    public val htmlBlock: HtmlBlock,
) {
    /** The base [InlinesStyling] derived from the [paragraph] styling, used as a fallback for inline rendering. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public val baseInlinesStyling: InlinesStyling = paragraph.inlinesStyling

    /** Styling for Markdown paragraph blocks. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Paragraph(override val inlinesStyling: InlinesStyling) : WithInlinesStyling {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Paragraph

            return inlinesStyling == other.inlinesStyling
        }

        override fun hashCode(): Int = inlinesStyling.hashCode()

        override fun toString(): String = "Paragraph(inlinesStyling=$inlinesStyling)"

        /** Companion object for [Paragraph]. */
        public companion object
    }

    /** Styling for Markdown heading blocks (H1–H6). */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Heading(
        /** Styling for H1 headings. */
        public val h1: H1,
        /** Styling for H2 headings. */
        public val h2: H2,
        /** Styling for H3 headings. */
        public val h3: H3,
        /** Styling for H4 headings. */
        public val h4: H4,
        /** Styling for H5 headings. */
        public val h5: H5,
        /** Styling for H6 headings. */
        public val h6: H6,
    ) {
        /** Common styling contract for all heading levels (H1–H6), combining inline and underline styling. */
        public sealed interface HN : WithInlinesStyling, WithUnderline {
            /** The padding applied around the heading content. */
            public val padding: PaddingValues
        }

        /** Styling for the H1 heading level. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class H1(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as H1

                if (inlinesStyling != other.inlinesStyling) return false
                if (underlineWidth != other.underlineWidth) return false
                if (underlineColor != other.underlineColor) return false
                if (underlineGap != other.underlineGap) return false
                if (padding != other.padding) return false

                return true
            }

            override fun hashCode(): Int {
                var result = inlinesStyling.hashCode()
                result = 31 * result + underlineWidth.hashCode()
                result = 31 * result + underlineColor.hashCode()
                result = 31 * result + underlineGap.hashCode()
                result = 31 * result + padding.hashCode()
                return result
            }

            override fun toString(): String {
                return "H1(" +
                    "inlinesStyling=$inlinesStyling, " +
                    "underlineWidth=$underlineWidth, " +
                    "underlineColor=$underlineColor, " +
                    "underlineGap=$underlineGap, " +
                    "padding=$padding" +
                    ")"
            }

            /** Companion object for [H1]. */
            public companion object
        }

        /** Styling for the H2 heading level. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class H2(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as H2

                if (inlinesStyling != other.inlinesStyling) return false
                if (underlineWidth != other.underlineWidth) return false
                if (underlineColor != other.underlineColor) return false
                if (underlineGap != other.underlineGap) return false
                if (padding != other.padding) return false

                return true
            }

            override fun hashCode(): Int {
                var result = inlinesStyling.hashCode()
                result = 31 * result + underlineWidth.hashCode()
                result = 31 * result + underlineColor.hashCode()
                result = 31 * result + underlineGap.hashCode()
                result = 31 * result + padding.hashCode()
                return result
            }

            override fun toString(): String {
                return "H2(" +
                    "inlinesStyling=$inlinesStyling, " +
                    "underlineWidth=$underlineWidth, " +
                    "underlineColor=$underlineColor, " +
                    "underlineGap=$underlineGap, " +
                    "padding=$padding" +
                    ")"
            }

            /** Companion object for [H2]. */
            public companion object
        }

        /** Styling for the H3 heading level. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class H3(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as H3

                if (inlinesStyling != other.inlinesStyling) return false
                if (underlineWidth != other.underlineWidth) return false
                if (underlineColor != other.underlineColor) return false
                if (underlineGap != other.underlineGap) return false
                if (padding != other.padding) return false

                return true
            }

            override fun hashCode(): Int {
                var result = inlinesStyling.hashCode()
                result = 31 * result + underlineWidth.hashCode()
                result = 31 * result + underlineColor.hashCode()
                result = 31 * result + underlineGap.hashCode()
                result = 31 * result + padding.hashCode()
                return result
            }

            override fun toString(): String {
                return "H3(" +
                    "inlinesStyling=$inlinesStyling, " +
                    "underlineWidth=$underlineWidth, " +
                    "underlineColor=$underlineColor, " +
                    "underlineGap=$underlineGap, " +
                    "padding=$padding" +
                    ")"
            }

            /** Companion object for [H3]. */
            public companion object
        }

        /** Styling for the H4 heading level. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class H4(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as H4

                if (inlinesStyling != other.inlinesStyling) return false
                if (underlineWidth != other.underlineWidth) return false
                if (underlineColor != other.underlineColor) return false
                if (underlineGap != other.underlineGap) return false
                if (padding != other.padding) return false

                return true
            }

            override fun hashCode(): Int {
                var result = inlinesStyling.hashCode()
                result = 31 * result + underlineWidth.hashCode()
                result = 31 * result + underlineColor.hashCode()
                result = 31 * result + underlineGap.hashCode()
                result = 31 * result + padding.hashCode()
                return result
            }

            override fun toString(): String {
                return "H4(" +
                    "inlinesStyling=$inlinesStyling, " +
                    "underlineWidth=$underlineWidth, " +
                    "underlineColor=$underlineColor, " +
                    "underlineGap=$underlineGap, " +
                    "padding=$padding" +
                    ")"
            }

            /** Companion object for [H4]. */
            public companion object
        }

        /** Styling for the H5 heading level. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class H5(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as H5

                if (inlinesStyling != other.inlinesStyling) return false
                if (underlineWidth != other.underlineWidth) return false
                if (underlineColor != other.underlineColor) return false
                if (underlineGap != other.underlineGap) return false
                if (padding != other.padding) return false

                return true
            }

            override fun hashCode(): Int {
                var result = inlinesStyling.hashCode()
                result = 31 * result + underlineWidth.hashCode()
                result = 31 * result + underlineColor.hashCode()
                result = 31 * result + underlineGap.hashCode()
                result = 31 * result + padding.hashCode()
                return result
            }

            override fun toString(): String {
                return "H5(" +
                    "inlinesStyling=$inlinesStyling, " +
                    "underlineWidth=$underlineWidth, " +
                    "underlineColor=$underlineColor, " +
                    "underlineGap=$underlineGap, " +
                    "padding=$padding" +
                    ")"
            }

            /** Companion object for [H5]. */
            public companion object
        }

        /** Styling for the H6 heading level. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class H6(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as H6

                if (inlinesStyling != other.inlinesStyling) return false
                if (underlineWidth != other.underlineWidth) return false
                if (underlineColor != other.underlineColor) return false
                if (underlineGap != other.underlineGap) return false
                if (padding != other.padding) return false

                return true
            }

            override fun hashCode(): Int {
                var result = inlinesStyling.hashCode()
                result = 31 * result + underlineWidth.hashCode()
                result = 31 * result + underlineColor.hashCode()
                result = 31 * result + underlineGap.hashCode()
                result = 31 * result + padding.hashCode()
                return result
            }

            override fun toString(): String {
                return "H6(" +
                    "inlinesStyling=$inlinesStyling, " +
                    "underlineWidth=$underlineWidth, " +
                    "underlineColor=$underlineColor, " +
                    "underlineGap=$underlineGap, " +
                    "padding=$padding" +
                    ")"
            }

            /** Companion object for [H6]. */
            public companion object
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Heading

            if (h1 != other.h1) return false
            if (h2 != other.h2) return false
            if (h3 != other.h3) return false
            if (h4 != other.h4) return false
            if (h5 != other.h5) return false
            if (h6 != other.h6) return false

            return true
        }

        override fun hashCode(): Int {
            var result = h1.hashCode()
            result = 31 * result + h2.hashCode()
            result = 31 * result + h3.hashCode()
            result = 31 * result + h4.hashCode()
            result = 31 * result + h5.hashCode()
            result = 31 * result + h6.hashCode()
            return result
        }

        override fun toString(): String = "Heading(h1=$h1, h2=$h2, h3=$h3, h4=$h4, h5=$h5, h6=$h6)"

        /** Companion object for [Heading]. */
        public companion object
    }

    /** Styling for Markdown block quote elements, including the decorative left-side vertical bar. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class BlockQuote(
        /** The padding applied inside the block quote container. */
        public val padding: PaddingValues,
        /** The width of the decorative left-side vertical bar. */
        public val lineWidth: Dp,
        /** The color of the decorative left-side vertical bar. */
        public val lineColor: Color,
        /** The optional path effect applied to the decorative bar stroke. */
        public val pathEffect: PathEffect?,
        /** The stroke cap style for the decorative bar. */
        public val strokeCap: StrokeCap,
        /** The text color for block quote content. */
        public val textColor: Color,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BlockQuote

            if (padding != other.padding) return false
            if (lineWidth != other.lineWidth) return false
            if (lineColor != other.lineColor) return false
            if (pathEffect != other.pathEffect) return false
            if (strokeCap != other.strokeCap) return false
            if (textColor != other.textColor) return false

            return true
        }

        override fun hashCode(): Int {
            var result = padding.hashCode()
            result = 31 * result + lineWidth.hashCode()
            result = 31 * result + lineColor.hashCode()
            result = 31 * result + (pathEffect?.hashCode() ?: 0)
            result = 31 * result + strokeCap.hashCode()
            result = 31 * result + textColor.hashCode()
            return result
        }

        override fun toString(): String {
            return "BlockQuote(" +
                "padding=$padding, " +
                "lineWidth=$lineWidth, " +
                "lineColor=$lineColor, " +
                "pathEffect=$pathEffect, " +
                "strokeCap=$strokeCap, " +
                "textColor=$textColor" +
                ")"
        }

        /** Companion object for [BlockQuote]. */
        public companion object
    }

    /** Styling for Markdown list blocks, covering both ordered and unordered list variants. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class List(
        /** Styling for ordered (numbered) list blocks. */
        public val ordered: Ordered,
        /** Styling for unordered (bulleted) list blocks. */
        public val unordered: Unordered,
    ) {
        /** Styling for ordered (numbered) Markdown list blocks. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Ordered(
            /** The text style applied to the item number markers. */
            public val numberStyle: TextStyle,
            /** The horizontal gap between the item number and its content. */
            public val numberContentGap: Dp,
            /** The minimum width reserved for item number markers. */
            public val numberMinWidth: Dp,
            /** The text alignment for item number markers. */
            public val numberTextAlign: TextAlign,
            /** The vertical spacing between list items in loose lists. */
            public val itemVerticalSpacing: Dp,
            /** The vertical spacing between list items in tight lists. */
            public val itemVerticalSpacingTight: Dp,
            /** The padding applied around the ordered list container. */
            public val padding: PaddingValues,
            /** The number format styles applied at each nesting level. */
            public val numberFormatStyles: NumberFormatStyles,
        ) {
            /**
             * Holds the [NumberFormatStyle] to apply at the first, second, and third nesting levels of an ordered list.
             */
            @GenerateDataFunctions
            public class NumberFormatStyles(
                /** The number format style applied at the first (outermost) nesting level. */
                public val firstLevel: NumberFormatStyle,
                /** The number format style applied at the second nesting level; defaults to [firstLevel]. */
                public val secondLevel: NumberFormatStyle = firstLevel,
                /** The number format style applied at the third nesting level; defaults to [secondLevel]. */
                public val thirdLevel: NumberFormatStyle = secondLevel,
            ) {
                /**
                 * Defines the format used to render item numbers in an ordered list. Implementations determine how a
                 * positive integer is converted to its display string.
                 */
                public sealed interface NumberFormatStyle {
                    /** Converts a positive [number] to its display string for use in list item markers. */
                    public fun formatNumber(number: Int): String

                    /** Formats list item numbers as decimal digits (e.g., 1, 2, 3). */
                    public object Decimal : NumberFormatStyle {
                        override fun formatNumber(number: Int): String {
                            require(number >= 0) { "Input must not be a negative integer" }

                            return number.toString()
                        }
                    }

                    /** Formats list item numbers as lowercase Roman numerals (e.g., i, ii, iii). */
                    public object Roman : NumberFormatStyle {
                        override fun formatNumber(number: Int): String {
                            // Roman numerals can't represent 0; just render it as the literal "0".
                            if (number < 1) return number.toString()

                            val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
                            val symbols = arrayOf("m", "cm", "d", "cd", "c", "xc", "l", "xl", "x", "ix", "v", "iv", "i")
                            var remaining = number

                            return buildString {
                                for (i in values.indices) {
                                    while (remaining >= values[i]) {
                                        append(symbols[i])
                                        remaining -= values[i]
                                    }
                                }
                            }
                        }
                    }

                    /** Formats list item numbers as lowercase alphabetical labels (e.g., a, b, c, aa, ab). */
                    public object Alphabetical : NumberFormatStyle {
                        override fun formatNumber(number: Int): String {
                            // Letters can't represent 0; just render it as the literal "0".
                            if (number < 1) return number.toString()

                            var num = number
                            return buildString {
                                    while (num > 0) {
                                        num--
                                        val remainder = num % 26
                                        append('a' + remainder)
                                        num /= 26
                                    }
                                }
                                .reversed()
                        }
                    }
                }

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as NumberFormatStyles

                    if (firstLevel != other.firstLevel) return false
                    if (secondLevel != other.secondLevel) return false
                    if (thirdLevel != other.thirdLevel) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = firstLevel.hashCode()
                    result = 31 * result + secondLevel.hashCode()
                    result = 31 * result + thirdLevel.hashCode()
                    return result
                }

                override fun toString(): String =
                    "NumberFormatStyles(" +
                        "firstLevel=$firstLevel, " +
                        "secondLevel=$secondLevel, " +
                        "thirdLevel=$thirdLevel" +
                        ")"
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Ordered

                if (numberStyle != other.numberStyle) return false
                if (numberContentGap != other.numberContentGap) return false
                if (numberMinWidth != other.numberMinWidth) return false
                if (numberTextAlign != other.numberTextAlign) return false
                if (itemVerticalSpacing != other.itemVerticalSpacing) return false
                if (itemVerticalSpacingTight != other.itemVerticalSpacingTight) return false
                if (padding != other.padding) return false
                if (numberFormatStyles != other.numberFormatStyles) return false

                return true
            }

            override fun hashCode(): Int {
                var result = numberStyle.hashCode()
                result = 31 * result + numberContentGap.hashCode()
                result = 31 * result + numberMinWidth.hashCode()
                result = 31 * result + numberTextAlign.hashCode()
                result = 31 * result + itemVerticalSpacing.hashCode()
                result = 31 * result + itemVerticalSpacingTight.hashCode()
                result = 31 * result + padding.hashCode()
                result = 31 * result + numberFormatStyles.hashCode()
                return result
            }

            override fun toString(): String {
                return "Ordered(" +
                    "numberStyle=$numberStyle, " +
                    "numberContentGap=$numberContentGap, " +
                    "numberMinWidth=$numberMinWidth, " +
                    "numberTextAlign=$numberTextAlign, " +
                    "itemVerticalSpacing=$itemVerticalSpacing, " +
                    "itemVerticalSpacingTight=$itemVerticalSpacingTight, " +
                    "padding=$padding, " +
                    "numberFormatStyles=$numberFormatStyles" +
                    ")"
            }

            /** Companion object for [Ordered]. */
            public companion object
        }

        /** Styling for unordered (bulleted) Markdown list blocks. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Unordered(
            /** The bullet character used for all nesting levels when [bulletCharStyles] is null. */
            public val bullet: Char?,
            /** The text style applied to bullet marker characters. */
            public val bulletStyle: TextStyle,
            /** The horizontal gap between the bullet marker and its content. */
            public val bulletContentGap: Dp,
            /** The vertical spacing between list items in loose lists. */
            public val itemVerticalSpacing: Dp,
            /** The vertical spacing between list items in tight lists. */
            public val itemVerticalSpacingTight: Dp,
            /** The padding applied around the unordered list container. */
            public val padding: PaddingValues,
            /** The minimum width reserved for bullet markers. */
            public val markerMinWidth: Dp,
            /** The per-level bullet characters; when non-null, overrides [bullet]. */
            public val bulletCharStyles: BulletCharStyles?,
        ) {
            /**
             * Holds the bullet characters to use at the first, second, and third nesting levels of an unordered list.
             */
            @GenerateDataFunctions
            public class BulletCharStyles(
                /** The bullet character used at the first (outermost) nesting level. */
                public val firstLevel: Char = '•',
                /** The bullet character used at the second nesting level; defaults to [firstLevel]. */
                public val secondLevel: Char = firstLevel,
                /** The bullet character used at the third nesting level; defaults to [secondLevel]. */
                public val thirdLevel: Char = secondLevel,
            ) {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (javaClass != other?.javaClass) return false

                    other as BulletCharStyles

                    if (firstLevel != other.firstLevel) return false
                    if (secondLevel != other.secondLevel) return false
                    if (thirdLevel != other.thirdLevel) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = firstLevel.hashCode()
                    result = 31 * result + secondLevel.hashCode()
                    result = 31 * result + thirdLevel.hashCode()
                    return result
                }

                override fun toString(): String =
                    "BulletCharStyles(" +
                        "firstLevel=$firstLevel, " +
                        "secondLevel=$secondLevel, " +
                        "thirdLevel=$thirdLevel" +
                        ")"
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Unordered

                if (bullet != other.bullet) return false
                if (bulletStyle != other.bulletStyle) return false
                if (bulletContentGap != other.bulletContentGap) return false
                if (itemVerticalSpacing != other.itemVerticalSpacing) return false
                if (itemVerticalSpacingTight != other.itemVerticalSpacingTight) return false
                if (padding != other.padding) return false
                if (markerMinWidth != other.markerMinWidth) return false
                if (bulletCharStyles != other.bulletCharStyles) return false

                return true
            }

            override fun hashCode(): Int {
                var result = bullet?.hashCode() ?: 0
                result = 31 * result + bulletStyle.hashCode()
                result = 31 * result + bulletContentGap.hashCode()
                result = 31 * result + itemVerticalSpacing.hashCode()
                result = 31 * result + itemVerticalSpacingTight.hashCode()
                result = 31 * result + padding.hashCode()
                result = 31 * result + markerMinWidth.hashCode()
                result = 31 * result + bulletCharStyles.hashCode()
                return result
            }

            override fun toString(): String {
                return "Unordered(" +
                    "bullet=$bullet, " +
                    "bulletStyle=$bulletStyle, " +
                    "bulletContentGap=$bulletContentGap, " +
                    "itemVerticalSpacing=$itemVerticalSpacing, " +
                    "itemVerticalSpacingTight=$itemVerticalSpacingTight, " +
                    "padding=$padding, " +
                    "markerMinWidth=$markerMinWidth, " +
                    "bulletCharByLevel=$bulletCharStyles" +
                    ")"
            }

            /** Companion object for [Unordered]. */
            public companion object
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as List

            if (ordered != other.ordered) return false
            if (unordered != other.unordered) return false

            return true
        }

        override fun hashCode(): Int {
            var result = ordered.hashCode()
            result = 31 * result + unordered.hashCode()
            return result
        }

        override fun toString(): String = "List(ordered=$ordered, unordered=$unordered)"

        /** Companion object for [List]. */
        public companion object
    }

    /** Styling for Markdown code blocks, covering both indented and fenced variants. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Code(
        /** Styling for indented code blocks. */
        public val indented: Indented,
        /** Styling for fenced code blocks. */
        public val fenced: Fenced,
    ) {
        /** Styling for indented Markdown code blocks. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Indented(
            /** The text style used to render code content. */
            public val editorTextStyle: TextStyle,
            /** The padding applied inside the code block container. */
            public val padding: PaddingValues,
            /** The shape of the code block container. */
            public val shape: Shape,
            /** The background color of the code block container. */
            public val background: Color,
            /** The width of the code block border. */
            public val borderWidth: Dp,
            /** The color of the code block border. */
            public val borderColor: Color,
            /** Whether the code block expands to fill the available width. */
            public val fillWidth: Boolean,
            /** Whether the code block scrolls horizontally when content overflows. */
            public val scrollsHorizontally: Boolean,
        ) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Indented

                if (fillWidth != other.fillWidth) return false
                if (scrollsHorizontally != other.scrollsHorizontally) return false
                if (editorTextStyle != other.editorTextStyle) return false
                if (padding != other.padding) return false
                if (shape != other.shape) return false
                if (background != other.background) return false
                if (borderWidth != other.borderWidth) return false
                if (borderColor != other.borderColor) return false

                return true
            }

            override fun hashCode(): Int {
                var result = fillWidth.hashCode()
                result = 31 * result + scrollsHorizontally.hashCode()
                result = 31 * result + editorTextStyle.hashCode()
                result = 31 * result + padding.hashCode()
                result = 31 * result + shape.hashCode()
                result = 31 * result + background.hashCode()
                result = 31 * result + borderWidth.hashCode()
                result = 31 * result + borderColor.hashCode()
                return result
            }

            override fun toString(): String {
                return "Indented(" +
                    "editorTextStyle=$editorTextStyle, " +
                    "padding=$padding, " +
                    "shape=$shape, " +
                    "background=$background, " +
                    "borderWidth=$borderWidth, " +
                    "borderColor=$borderColor, " +
                    "fillWidth=$fillWidth, " +
                    "scrollsHorizontally=$scrollsHorizontally" +
                    ")"
            }

            /** Companion object for [Indented]. */
            public companion object
        }

        /** Styling for fenced Markdown code blocks, including optional language info label rendering. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Fenced(
            /** The text style used to render code content. */
            public val editorTextStyle: TextStyle,
            /** The padding applied inside the code block container. */
            public val padding: PaddingValues,
            /** The shape of the code block container. */
            public val shape: Shape,
            /** The background color of the code block container. */
            public val background: Color,
            /** The width of the code block border. */
            public val borderWidth: Dp,
            /** The color of the code block border. */
            public val borderColor: Color,
            /** Whether the code block expands to fill the available width. */
            public val fillWidth: Boolean,
            /** Whether the code block scrolls horizontally when content overflows. */
            public val scrollsHorizontally: Boolean,
            /** The text style applied to the language info label. */
            public val infoTextStyle: TextStyle,
            /** The padding applied around the language info label. */
            public val infoPadding: PaddingValues,
            /** The position of the language info label relative to the code block. */
            public val infoPosition: InfoPosition,
        ) {
            /**
             * Controls where the language info string label is displayed relative to a fenced code block. Use [Hide] to
             * suppress the label entirely.
             */
            public enum class InfoPosition {
                /** Positions the info label at the top-start corner of the code block. */
                TopStart,
                /** Positions the info label at the top-center of the code block. */
                TopCenter,
                /** Positions the info label at the top-end corner of the code block. */
                TopEnd,
                /** Positions the info label at the bottom-start corner of the code block. */
                BottomStart,
                /** Positions the info label at the bottom-center of the code block. */
                BottomCenter,
                /** Positions the info label at the bottom-end corner of the code block. */
                BottomEnd,
                /** Hides the info label entirely. */
                Hide,
            }

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Fenced

                if (fillWidth != other.fillWidth) return false
                if (scrollsHorizontally != other.scrollsHorizontally) return false
                if (editorTextStyle != other.editorTextStyle) return false
                if (padding != other.padding) return false
                if (shape != other.shape) return false
                if (background != other.background) return false
                if (borderWidth != other.borderWidth) return false
                if (borderColor != other.borderColor) return false
                if (infoTextStyle != other.infoTextStyle) return false
                if (infoPadding != other.infoPadding) return false
                if (infoPosition != other.infoPosition) return false

                return true
            }

            override fun hashCode(): Int {
                var result = fillWidth.hashCode()
                result = 31 * result + scrollsHorizontally.hashCode()
                result = 31 * result + editorTextStyle.hashCode()
                result = 31 * result + padding.hashCode()
                result = 31 * result + shape.hashCode()
                result = 31 * result + background.hashCode()
                result = 31 * result + borderWidth.hashCode()
                result = 31 * result + borderColor.hashCode()
                result = 31 * result + infoTextStyle.hashCode()
                result = 31 * result + infoPadding.hashCode()
                result = 31 * result + infoPosition.hashCode()
                return result
            }

            override fun toString(): String {
                return "Fenced(" +
                    "editorTextStyle=$editorTextStyle, " +
                    "padding=$padding, " +
                    "shape=$shape, " +
                    "background=$background, " +
                    "borderWidth=$borderWidth, " +
                    "borderColor=$borderColor, " +
                    "fillWidth=$fillWidth, " +
                    "scrollsHorizontally=$scrollsHorizontally, " +
                    "infoTextStyle=$infoTextStyle, " +
                    "infoPadding=$infoPadding, " +
                    "infoPosition=$infoPosition" +
                    ")"
            }

            /** Companion object for [Fenced]. */
            public companion object
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Code

            if (indented != other.indented) return false
            if (fenced != other.fenced) return false

            return true
        }

        override fun hashCode(): Int {
            var result = indented.hashCode()
            result = 31 * result + fenced.hashCode()
            return result
        }

        override fun toString(): String = "Code(indented=$indented, fenced=$fenced)"

        /** Companion object for [Code]. */
        public companion object
    }

    /** Styling for Markdown image elements, controlling layout, scaling, and visual decoration. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Image(
        /** The alignment of the image within its container. */
        public val alignment: Alignment,
        /** The content scale strategy applied when rendering the image. */
        public val contentScale: ContentScale,
        /** The padding applied around the image. */
        public val padding: PaddingValues,
        /** The shape of the image container. */
        public val shape: Shape,
        /** The background color behind the image. */
        public val background: Color,
        /** The width of the image border. */
        public val borderWidth: Dp,
        /** The color of the image border. */
        public val borderColor: Color,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (alignment != other.alignment) return false
            if (contentScale != other.contentScale) return false
            if (padding != other.padding) return false
            if (shape != other.shape) return false
            if (background != other.background) return false
            if (borderWidth != other.borderWidth) return false
            if (borderColor != other.borderColor) return false

            return true
        }

        override fun hashCode(): Int {
            var result = alignment.hashCode()
            result = 31 * result + contentScale.hashCode()
            result = 31 * result + padding.hashCode()
            result = 31 * result + shape.hashCode()
            result = 31 * result + background.hashCode()
            result = 31 * result + borderWidth.hashCode()
            result = 31 * result + borderColor.hashCode()
            return result
        }

        override fun toString(): String {
            return "Image(" +
                "alignment=$alignment, " +
                "contentScale=$contentScale, " +
                "padding=$padding, " +
                "shape=$shape, " +
                "background=$background, " +
                "borderWidth=$borderWidth, " +
                "borderColor=$borderColor" +
                ")"
        }

        /** Companion object for [Image]. */
        public companion object
    }

    /** Styling for the Markdown thematic break (horizontal rule) element. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class ThematicBreak(
        /** The padding applied around the thematic break line. */
        public val padding: PaddingValues,
        /** The stroke width of the thematic break line. */
        public val lineWidth: Dp,
        /** The color of the thematic break line. */
        public val lineColor: Color,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ThematicBreak

            if (padding != other.padding) return false
            if (lineWidth != other.lineWidth) return false
            if (lineColor != other.lineColor) return false

            return true
        }

        override fun hashCode(): Int {
            var result = padding.hashCode()
            result = 31 * result + lineWidth.hashCode()
            result = 31 * result + lineColor.hashCode()
            return result
        }

        override fun toString(): String = "ThematicBreak(padding=$padding, lineWidth=$lineWidth, lineColor=$lineColor)"

        /** Companion object for [ThematicBreak]. */
        public companion object
    }

    /**
     * Styling for Markdown HTML block elements. HTML blocks are not rendered by default; this styling only takes effect
     * for custom renderers that opt in to rendering them.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class HtmlBlock(
        /** The text style used to render the HTML block content. */
        public val textStyle: TextStyle,
        /** The padding applied inside the HTML block container. */
        public val padding: PaddingValues,
        /** The shape of the HTML block container. */
        public val shape: Shape,
        /** The background color of the HTML block container. */
        public val background: Color,
        /** The width of the HTML block border. */
        public val borderWidth: Dp,
        /** The color of the HTML block border. */
        public val borderColor: Color,
        /** Whether the HTML block expands to fill the available width. */
        public val fillWidth: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as HtmlBlock

            if (fillWidth != other.fillWidth) return false
            if (textStyle != other.textStyle) return false
            if (padding != other.padding) return false
            if (shape != other.shape) return false
            if (background != other.background) return false
            if (borderWidth != other.borderWidth) return false
            if (borderColor != other.borderColor) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fillWidth.hashCode()
            result = 31 * result + textStyle.hashCode()
            result = 31 * result + padding.hashCode()
            result = 31 * result + shape.hashCode()
            result = 31 * result + background.hashCode()
            result = 31 * result + borderWidth.hashCode()
            result = 31 * result + borderColor.hashCode()
            return result
        }

        override fun toString(): String {
            return "HtmlBlock(" +
                "textStyle=$textStyle, " +
                "padding=$padding, " +
                "shape=$shape, " +
                "background=$background, " +
                "borderWidth=$borderWidth, " +
                "borderColor=$borderColor, " +
                "fillWidth=$fillWidth" +
                ")"
        }

        /** Companion object for [HtmlBlock]. */
        public companion object
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MarkdownStyling

        if (blockVerticalSpacing != other.blockVerticalSpacing) return false
        if (paragraph != other.paragraph) return false
        if (heading != other.heading) return false
        if (blockQuote != other.blockQuote) return false
        if (code != other.code) return false
        if (list != other.list) return false
        if (image != other.image) return false
        if (thematicBreak != other.thematicBreak) return false
        if (htmlBlock != other.htmlBlock) return false

        return true
    }

    override fun hashCode(): Int {
        var result = blockVerticalSpacing.hashCode()
        result = 31 * result + paragraph.hashCode()
        result = 31 * result + heading.hashCode()
        result = 31 * result + blockQuote.hashCode()
        result = 31 * result + code.hashCode()
        result = 31 * result + list.hashCode()
        result = 31 * result + image.hashCode()
        result = 31 * result + thematicBreak.hashCode()
        result = 31 * result + htmlBlock.hashCode()
        return result
    }

    override fun toString(): String {
        return "MarkdownStyling(" +
            "blockVerticalSpacing=$blockVerticalSpacing, " +
            "paragraph=$paragraph, " +
            "heading=$heading, " +
            "blockQuote=$blockQuote, " +
            "code=$code, " +
            "list=$list, " +
            "image=$image, " +
            "thematicBreak=$thematicBreak, " +
            "htmlBlock=$htmlBlock" +
            ")"
    }

    /** Companion object for [MarkdownStyling]. */
    public companion object
}

/**
 * Marks a Markdown rendering class as carrying [InlinesStyling] for its inline content, such as text spans within
 * paragraphs or headings.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface WithInlinesStyling {
    /** The styling applied to inline Markdown elements within this block. */
    public val inlinesStyling: InlinesStyling
}

/**
 * Marks a Markdown rendering class as carrying underline decoration properties, used by heading levels that display a
 * separator line below their content.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface WithUnderline {
    /** The stroke width of the underline decoration. */
    public val underlineWidth: Dp
    /** The color of the underline decoration. */
    public val underlineColor: Color
    /** The gap between the heading text and the underline decoration. */
    public val underlineGap: Dp
}

/**
 * Holds the styling applied to inline Markdown elements such as plain text, inline code, links (in all interactive
 * states), emphasis, strong emphasis, and inline HTML spans.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class InlinesStyling(
    /** The base text style applied to plain inline text. */
    public val textStyle: TextStyle,
    /** The span style applied to inline code spans. */
    public val inlineCode: SpanStyle,
    /** The span style applied to links in their default state. */
    public val link: SpanStyle,
    /** The span style applied to links in their disabled state. */
    public val linkDisabled: SpanStyle,
    /** The span style applied to links in their focused state. */
    public val linkFocused: SpanStyle,
    /** The span style applied to links in their hovered state. */
    public val linkHovered: SpanStyle,
    /** The span style applied to links in their pressed state. */
    public val linkPressed: SpanStyle,
    /** The span style applied to links in their visited state. */
    public val linkVisited: SpanStyle,
    /** The span style applied to emphasized (italic) text. */
    public val emphasis: SpanStyle,
    /** The span style applied to strongly emphasized (bold) text. */
    public val strongEmphasis: SpanStyle,
    /** The span style applied to inline HTML spans. */
    public val inlineHtml: SpanStyle,
) {
    /** The aggregated [TextLinkStyles] composed from [link], [linkFocused], [linkHovered], and [linkPressed]. */
    public val textLinkStyles: TextLinkStyles =
        TextLinkStyles(style = link, focusedStyle = linkFocused, hoveredStyle = linkHovered, pressedStyle = linkPressed)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InlinesStyling

        if (textStyle != other.textStyle) return false
        if (inlineCode != other.inlineCode) return false
        if (link != other.link) return false
        if (linkDisabled != other.linkDisabled) return false
        if (linkFocused != other.linkFocused) return false
        if (linkHovered != other.linkHovered) return false
        if (linkPressed != other.linkPressed) return false
        if (linkVisited != other.linkVisited) return false
        if (emphasis != other.emphasis) return false
        if (strongEmphasis != other.strongEmphasis) return false
        if (inlineHtml != other.inlineHtml) return false
        if (textLinkStyles != other.textLinkStyles) return false

        return true
    }

    override fun hashCode(): Int {
        var result = textStyle.hashCode()
        result = 31 * result + inlineCode.hashCode()
        result = 31 * result + link.hashCode()
        result = 31 * result + linkDisabled.hashCode()
        result = 31 * result + linkFocused.hashCode()
        result = 31 * result + linkHovered.hashCode()
        result = 31 * result + linkPressed.hashCode()
        result = 31 * result + linkVisited.hashCode()
        result = 31 * result + emphasis.hashCode()
        result = 31 * result + strongEmphasis.hashCode()
        result = 31 * result + inlineHtml.hashCode()
        result = 31 * result + textLinkStyles.hashCode()
        return result
    }

    override fun toString(): String {
        return "InlinesStyling(" +
            "textStyle=$textStyle, " +
            "inlineCode=$inlineCode, " +
            "link=$link, " +
            "linkDisabled=$linkDisabled, " +
            "linkFocused=$linkFocused, " +
            "linkHovered=$linkHovered, " +
            "linkPressed=$linkPressed, " +
            "linkVisited=$linkVisited, " +
            "emphasis=$emphasis, " +
            "strongEmphasis=$strongEmphasis, " +
            "inlineHtml=$inlineHtml, " +
            "textLinkStyles=$textLinkStyles" +
            ")"
    }

    /** Companion object for [InlinesStyling]. */
    public companion object
}

internal val InfoPosition.verticalAlignment
    get() =
        when (this) {
            TopStart,
            TopCenter,
            TopEnd -> Alignment.Top

            BottomStart,
            BottomCenter,
            BottomEnd -> Alignment.Bottom

            Hide -> null
        }

internal val InfoPosition.horizontalAlignment
    get() =
        when (this) {
            TopStart,
            BottomStart -> Alignment.Start

            TopCenter,
            BottomCenter -> Alignment.CenterHorizontally

            TopEnd,
            BottomEnd -> Alignment.End

            Hide -> null
        }
