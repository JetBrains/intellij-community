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

@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class MarkdownStyling(
    public val blockVerticalSpacing: Dp,
    public val paragraph: Paragraph,
    public val heading: Heading,
    public val blockQuote: BlockQuote,
    public val code: Code,
    public val list: List,
    public val image: Image,
    public val thematicBreak: ThematicBreak,
    public val htmlBlock: HtmlBlock,
) {
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public val baseInlinesStyling: InlinesStyling = paragraph.inlinesStyling

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

        public companion object
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Heading(
        public val h1: H1,
        public val h2: H2,
        public val h3: H3,
        public val h4: H4,
        public val h5: H5,
        public val h6: H6,
    ) {
        public sealed interface HN : WithInlinesStyling, WithUnderline {
            public val padding: PaddingValues
        }

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

            public companion object
        }

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

            public companion object
        }

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

            public companion object
        }

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

            public companion object
        }

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

            public companion object
        }

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

        public companion object
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class BlockQuote(
        public val padding: PaddingValues,
        public val lineWidth: Dp,
        public val lineColor: Color,
        public val pathEffect: PathEffect?,
        public val strokeCap: StrokeCap,
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

        public companion object
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class List(public val ordered: Ordered, public val unordered: Unordered) {
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Ordered(
            public val numberStyle: TextStyle,
            public val numberContentGap: Dp,
            public val numberMinWidth: Dp,
            public val numberTextAlign: TextAlign,
            public val itemVerticalSpacing: Dp,
            public val itemVerticalSpacingTight: Dp,
            public val padding: PaddingValues,
            public val numberFormatStyles: NumberFormatStyles,
        ) {
            @GenerateDataFunctions
            public class NumberFormatStyles(
                public val firstLevel: NumberFormatStyle,
                public val secondLevel: NumberFormatStyle = firstLevel,
                public val thirdLevel: NumberFormatStyle = secondLevel,
            ) {
                public sealed interface NumberFormatStyle {
                    public fun formatNumber(number: Int): String

                    public object Decimal : NumberFormatStyle {
                        override fun formatNumber(number: Int): String {
                            require(number > 0) { "Input must be a positive integer" }

                            return number.toString()
                        }
                    }

                    public object Roman : NumberFormatStyle {
                        override fun formatNumber(number: Int): String {
                            require(number > 0) { "Input must be a positive integer" }

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

                    public object Alphabetical : NumberFormatStyle {
                        override fun formatNumber(number: Int): String {
                            require(number > 0) { "Input must be a positive integer" }

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

            public companion object
        }

        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Unordered(
            public val bullet: Char?,
            public val bulletStyle: TextStyle,
            public val bulletContentGap: Dp,
            public val itemVerticalSpacing: Dp,
            public val itemVerticalSpacingTight: Dp,
            public val padding: PaddingValues,
            public val markerMinWidth: Dp,
            public val bulletCharStyles: BulletCharStyles?,
        ) {
            @GenerateDataFunctions
            public class BulletCharStyles(
                public val firstLevel: Char = '•',
                public val secondLevel: Char = firstLevel,
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

        public companion object
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Code(public val indented: Indented, public val fenced: Fenced) {
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Indented(
            public val editorTextStyle: TextStyle,
            public val padding: PaddingValues,
            public val shape: Shape,
            public val background: Color,
            public val borderWidth: Dp,
            public val borderColor: Color,
            public val fillWidth: Boolean,
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

            public companion object
        }

        @ApiStatus.Experimental
        @ExperimentalJewelApi
        @GenerateDataFunctions
        public class Fenced(
            public val editorTextStyle: TextStyle,
            public val padding: PaddingValues,
            public val shape: Shape,
            public val background: Color,
            public val borderWidth: Dp,
            public val borderColor: Color,
            public val fillWidth: Boolean,
            public val scrollsHorizontally: Boolean,
            public val infoTextStyle: TextStyle,
            public val infoPadding: PaddingValues,
            public val infoPosition: InfoPosition,
        ) {
            public enum class InfoPosition {
                TopStart,
                TopCenter,
                TopEnd,
                BottomStart,
                BottomCenter,
                BottomEnd,
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

        public companion object
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class Image(
        public val alignment: Alignment,
        public val contentScale: ContentScale,
        public val padding: PaddingValues,
        public val shape: Shape,
        public val background: Color,
        public val borderWidth: Dp,
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

        public companion object
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class ThematicBreak(
        public val padding: PaddingValues,
        public val lineWidth: Dp,
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

        public companion object
    }

    @ApiStatus.Experimental
    @ExperimentalJewelApi
    @GenerateDataFunctions
    public class HtmlBlock(
        public val textStyle: TextStyle,
        public val padding: PaddingValues,
        public val shape: Shape,
        public val background: Color,
        public val borderWidth: Dp,
        public val borderColor: Color,
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

    public companion object
}

@ApiStatus.Experimental
@ExperimentalJewelApi
public interface WithInlinesStyling {
    public val inlinesStyling: InlinesStyling
}

@ApiStatus.Experimental
@ExperimentalJewelApi
public interface WithUnderline {
    public val underlineWidth: Dp
    public val underlineColor: Color
    public val underlineGap: Dp
}

@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class InlinesStyling(
    public val textStyle: TextStyle,
    public val inlineCode: SpanStyle,
    public val link: SpanStyle,
    public val linkDisabled: SpanStyle,
    public val linkFocused: SpanStyle,
    public val linkHovered: SpanStyle,
    public val linkPressed: SpanStyle,
    public val linkVisited: SpanStyle,
    public val emphasis: SpanStyle,
    public val strongEmphasis: SpanStyle,
    public val inlineHtml: SpanStyle,
) {
    @Deprecated("Use variant without renderInlineHtml instead.", level = DeprecationLevel.HIDDEN)
    public constructor(
        textStyle: TextStyle,
        inlineCode: SpanStyle,
        link: SpanStyle,
        linkDisabled: SpanStyle,
        linkFocused: SpanStyle,
        linkHovered: SpanStyle,
        linkPressed: SpanStyle,
        linkVisited: SpanStyle,
        emphasis: SpanStyle,
        strongEmphasis: SpanStyle,
        inlineHtml: SpanStyle,
        @Suppress("UnusedPrivateProperty") renderInlineHtml: Boolean,
    ) : this(
        textStyle,
        inlineCode,
        link,
        linkDisabled,
        linkFocused,
        linkHovered,
        linkPressed,
        linkVisited,
        emphasis,
        strongEmphasis,
        inlineHtml,
    )

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
