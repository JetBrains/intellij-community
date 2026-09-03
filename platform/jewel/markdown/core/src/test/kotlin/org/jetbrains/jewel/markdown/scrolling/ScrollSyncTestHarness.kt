// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.testing.createMarkdownTestDividerStyle
import org.jetbrains.jewel.markdown.testing.createMarkdownTestScrollbarStyle
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.jetbrains.jewel.markdown.testing.createMarkdownTestThemeDefinition
import org.jetbrains.jewel.ui.component.styling.LocalDividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle

/**
 * Composes [content] with a scroll-syncing renderer and everything it reads from the composition, then runs [action]
 * against [scrollState] and the synchronizer built for it.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("ImplicitUnitReturnType")
internal fun <S : ScrollableState> runScrollSyncTest(
    scrollState: S,
    styling: MarkdownStyling = createMarkdownTestStyling(),
    parseEmbeddedHtml: Boolean = false,
    content: @Composable (MarkdownProcessor) -> Unit,
    action: suspend (S, ContinuousScrollingSynchronizer) -> Unit,
) = runComposeUiTest {
    val synchronizer = ScrollingSynchronizer.createContinuous(scrollState)!!
    val renderer = ScrollSyncMarkdownBlockRenderer(styling, emptyList(), DefaultInlineMarkdownRenderer(emptyList()))
    val markdownMode = MarkdownMode.EditorPreview(synchronizer)
    val processor = MarkdownProcessor(markdownMode = markdownMode, parseEmbeddedHtml = parseEmbeddedHtml)
    var scope: CoroutineScope? = null

    setContent {
        scope = rememberCoroutineScope()
        CompositionLocalProvider(
            LocalMarkdownStyling provides styling,
            LocalMarkdownMode provides markdownMode,
            LocalMarkdownProcessor provides processor,
            LocalMarkdownBlockRenderer provides renderer,
            LocalCodeHighlighter provides NoOpCodeHighlighter,
            LocalDividerStyle provides createMarkdownTestDividerStyle(),
            LocalScrollbarStyle provides createMarkdownTestScrollbarStyle(),
            LocalDensity provides Density(1f),
        ) {
            JewelTheme(createMarkdownTestThemeDefinition()) { content(processor) }
        }
    }

    scope!!.launch { action(scrollState, synchronizer) }
    waitForIdle()
}

/** An editor layout where every source line is 10px tall, so line `n` starts at offset `10n`. */
internal val tenPixelsPerLine: (Int) -> Int = { line -> line * 10 }

/** Runs a few frames, so a scroll and everything it sets off has settled. */
internal suspend fun settle() {
    repeat(5) { withFrameNanos {} }
}
