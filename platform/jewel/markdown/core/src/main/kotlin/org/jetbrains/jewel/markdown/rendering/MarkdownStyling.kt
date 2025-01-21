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
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.BottomCenter
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.BottomEnd
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.BottomStart
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.Hide
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.TopCenter
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.TopEnd
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition.TopStart

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
    @GenerateDataFunctions
    public class Paragraph(override val inlinesStyling: InlinesStyling) : WithInlinesStyling {
        public companion object
    }

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

        @GenerateDataFunctions
        public class H1(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            public companion object
        }

        @GenerateDataFunctions
        public class H2(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            public companion object
        }

        @GenerateDataFunctions
        public class H3(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            public companion object
        }

        @GenerateDataFunctions
        public class H4(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            public companion object
        }

        @GenerateDataFunctions
        public class H5(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            public companion object
        }

        @GenerateDataFunctions
        public class H6(
            override val inlinesStyling: InlinesStyling,
            override val underlineWidth: Dp,
            override val underlineColor: Color,
            override val underlineGap: Dp,
            override val padding: PaddingValues,
        ) : HN {
            public companion object
        }

        public companion object
    }

    @GenerateDataFunctions
    public class BlockQuote(
        public val padding: PaddingValues,
        public val lineWidth: Dp,
        public val lineColor: Color,
        public val pathEffect: PathEffect?,
        public val strokeCap: StrokeCap,
        public val textColor: Color,
    ) {
        public companion object
    }

    @GenerateDataFunctions
    public class List(public val ordered: Ordered, public val unordered: Unordered) {
        @GenerateDataFunctions
        public class Ordered(
            public val numberStyle: TextStyle,
            public val numberContentGap: Dp,
            public val numberMinWidth: Dp,
            public val numberTextAlign: TextAlign,
            public val itemVerticalSpacing: Dp,
            public val itemVerticalSpacingTight: Dp,
            public val padding: PaddingValues,
        ) {
            public companion object
        }

        @GenerateDataFunctions
        public class Unordered(
            public val bullet: Char?,
            public val bulletStyle: TextStyle,
            public val bulletContentGap: Dp,
            public val itemVerticalSpacing: Dp,
            public val itemVerticalSpacingTight: Dp,
            public val padding: PaddingValues,
        ) {
            public companion object
        }

        public companion object
    }

    @GenerateDataFunctions
    public class Code(public val indented: Indented, public val fenced: Fenced) {
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
            public companion object
        }

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

            public companion object
        }

        public companion object
    }

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
        public companion object
    }

    @GenerateDataFunctions
    public class ThematicBreak(
        public val padding: PaddingValues,
        public val lineWidth: Dp,
        public val lineColor: Color,
    ) {
        public companion object
    }

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
        public companion object
    }

    public companion object
}

public interface WithInlinesStyling {
    public val inlinesStyling: InlinesStyling
}

public interface WithUnderline {
    public val underlineWidth: Dp
    public val underlineColor: Color
    public val underlineGap: Dp
}

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
    public val renderInlineHtml: Boolean,
) {
    public val textLinkStyles: TextLinkStyles =
        TextLinkStyles(style = link, focusedStyle = linkFocused, hoveredStyle = linkHovered, pressedStyle = linkPressed)

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
