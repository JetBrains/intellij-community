// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/** An immutable, structured body for a [GotItTooltip]. Build one with [buildGotItBody]. */
@Stable public class GotItBody internal constructor(internal val segments: List<GotItBodySegment>)

/**
 * DSL builder for [GotItBody]. All methods return `this` to allow chaining.
 *
 * Use [buildGotItBody] to create a [GotItBody] from this builder.
 */
public class GotItBodyBuilder @PublishedApi internal constructor() {
    private val segments = mutableListOf<GotItBodySegment>()

    /** Appends plain text. */
    public fun append(@Nls text: String): GotItBodyBuilder = apply { segments += GotItBodySegment.Plain(text) }

    /** Appends text rendered in **bold**. */
    public fun bold(@Nls text: String): GotItBodyBuilder = apply { segments += GotItBodySegment.Bold(text) }

    /** Appends text rendered in a `monospace` font with a distinct background color. */
    public fun code(@Nls text: String): GotItBodyBuilder = apply {
        if (text.isNotEmpty()) segments += GotItBodySegment.Code(text)
    }

    /** Appends a clickable inline link. */
    public fun link(@Nls text: String, action: () -> Unit): GotItBodyBuilder = apply {
        segments += GotItBodySegment.InlineLink(text, action)
    }

    /** Appends a link that opens [uri] in the browser when clicked. */
    public fun browserLink(@Nls text: String, uri: String): GotItBodyBuilder = apply {
        segments += GotItBodySegment.BrowserLink(text, uri)
    }

    /** Appends an inline icon rendered in a 1em square placeholder, vertically centred with the surrounding text. */
    public fun icon(contentDescription: String?, content: @Composable () -> Unit): GotItBodyBuilder = apply {
        segments += GotItBodySegment.Icon(contentDescription, content)
    }

    @PublishedApi internal fun build(): GotItBody = GotItBody(segments.toList())
}

internal sealed interface GotItBodySegment {
    @JvmInline value class Plain(val text: String) : GotItBodySegment

    @JvmInline value class Bold(val text: String) : GotItBodySegment

    @JvmInline value class Code(val text: String) : GotItBodySegment

    data class InlineLink(val text: String, val action: () -> Unit) : GotItBodySegment

    data class BrowserLink(val text: String, val uri: String) : GotItBodySegment

    data class Icon(val contentDescription: String?, val content: @Composable () -> Unit) : GotItBodySegment
}

@VisibleForTesting
public fun buildBodyAnnotatedString(body: GotItBody, styleColors: GotItColors): AnnotatedString = buildAnnotatedString {
    val linkStyles =
        TextLinkStyles(
            style = SpanStyle(color = styleColors.link, textDecoration = TextDecoration.None),
            hoveredStyle = SpanStyle(color = styleColors.link, textDecoration = TextDecoration.Underline),
            focusedStyle = SpanStyle(color = styleColors.link, textDecoration = TextDecoration.Underline),
            pressedStyle = SpanStyle(color = styleColors.link, textDecoration = TextDecoration.None),
        )
    body.segments.forEachIndexed { index, segment ->
        when (segment) {
            is GotItBodySegment.Plain -> append(segment.text)
            is GotItBodySegment.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment.text) }
            is GotItBodySegment.Code -> appendInlineContent(codeInlineId(segment.text), segment.text)
            is GotItBodySegment.InlineLink ->
                withLink(
                    LinkAnnotation.Clickable(
                        tag = segment.text,
                        styles = linkStyles,
                        linkInteractionListener = { segment.action() },
                    )
                ) {
                    withStyle(SpanStyle(color = styleColors.link)) { append(segment.text) }
                }
            is GotItBodySegment.BrowserLink -> {
                withLink(LinkAnnotation.Url(url = segment.uri, styles = linkStyles)) {
                    withStyle(SpanStyle(color = styleColors.link)) { append(segment.text) }
                }
                append(" ")
                appendInlineContent(browserLinkArrowId(index), "↗")
            }
            is GotItBodySegment.Icon -> appendInlineContent(iconInlineId(index), segment.contentDescription ?: "\uFFFD")
        }
    }
}

private fun codeInlineId(text: String) = "jewel:code:$text"

private fun iconInlineId(index: Int) = "jewel:icon:$index"

private fun browserLinkArrowId(index: Int) = "jewel:browser-link-arrow:$index"

/**
 * Builds a [Map] of [InlineTextContent] entries for code and icon segments in [body], keyed by the ID used in the
 * [AnnotatedString] produced by [buildBodyAnnotatedString]. Pass the result to the `inlineContent` parameter of a
 * `Text` composable.
 */
@VisibleForTesting
public fun buildInlineContent(
    body: GotItBody,
    styleColors: GotItColors,
    editorFontStyle: TextStyle,
    externalLinkIconKey: IconKey = AllIconsKeys.Ide.External_link_arrow,
): Map<String, InlineTextContent> {
    // InlineTextContent requires a Placeholder whose width is committed upfront, before the text is laid out.
    // There is no callback to measure the actual rendered width and feed it back without a two-pass approach
    // (e.g. SubcomposeLayout + state recomposition), which would add significant complexity and a one-frame
    // flicker on first render.
    //
    // Instead, we estimate the placeholder width from style information alone:
    //   charAdvance = naturalCharWidth + letterSpacing
    //
    // naturalCharWidth is approximated as 0.6 em — a reasonable midpoint for common monospace fonts
    // (JetBrains Mono ≈ 0.577, Consolas ≈ 0.55, Courier New ≈ 0.60). letterSpacing is the extra inter-character
    // gap defined in the style; we normalize it to em so it can be summed directly with the ratio.
    // The result will be slightly off for unusual fonts or font sizes, but will never clip the text
    // (we err on the side of a marginally wider placeholder).
    val letterSpacingEm: Float =
        when {
            editorFontStyle.letterSpacing.isEm -> editorFontStyle.letterSpacing.value
            editorFontStyle.letterSpacing.isSp && editorFontStyle.fontSize.isSp && editorFontStyle.fontSize.value > 0 ->
                editorFontStyle.letterSpacing.value / editorFontStyle.fontSize.value
            else -> 0f
        }
    val charAdvanceEm = 0.6f + letterSpacingEm

    val result = mutableMapOf<String, InlineTextContent>()
    for ((index, segment) in body.segments.withIndex()) {
        when (segment) {
            is GotItBodySegment.Code -> {
                val id = codeInlineId(segment.text)
                if (id !in result) {
                    result[id] =
                        InlineTextContent(
                            Placeholder(
                                width = (segment.text.length * charAdvanceEm + 0.7f).em,
                                height = 1.3.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            )
                        ) { codeText ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier.fillMaxSize()
                                        // The code text is already accessible via the alternate text in the outer
                                        // AnnotatedString. Suppressing here avoids a duplicate semantics node
                                        // that would cause ambiguous matches in UI tests.
                                        .clearAndSetSemantics {}
                                        .background(styleColors.codeBackground, RoundedCornerShape(3.dp))
                                        .border(0.5.dp, styleColors.codeForeground, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 2.dp),
                            ) {
                                BasicText(
                                    text = codeText,
                                    style = editorFontStyle.copy(color = styleColors.codeForeground),
                                )
                            }
                        }
                }
            }
            is GotItBodySegment.Icon -> {
                result[iconInlineId(index)] =
                    InlineTextContent(
                        Placeholder(
                            width = 1.em,
                            height = 1.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        )
                    ) { _ ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { segment.content() }
                    }
            }
            is GotItBodySegment.BrowserLink -> {
                result[browserLinkArrowId(index)] =
                    InlineTextContent(
                        Placeholder(
                            width = 1.em,
                            height = 1.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        )
                    ) { _ ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(key = externalLinkIconKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
            }
            else -> {
                // do nothing
            }
        }
    }
    return result
}

/**
 * Builds a [GotItBody] using the [GotItBodyBuilder] DSL.
 *
 * Example:
 * ```kotlin
 * val body = buildGotItBody {
 *     append("Press ")
 *     bold("Resume")
 *     append(" to continue, or ")
 *     link("open the docs") { openUrl("https://example.com") }
 *     append(".")
 * }
 * ```
 */
public inline fun buildGotItBody(builder: GotItBodyBuilder.() -> Unit): GotItBody =
    GotItBodyBuilder().apply(builder).build()
