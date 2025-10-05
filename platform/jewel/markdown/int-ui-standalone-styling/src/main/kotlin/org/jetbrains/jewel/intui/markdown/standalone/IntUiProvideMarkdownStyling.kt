// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.intui.markdown.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.ImageSourceResolver
import org.jetbrains.jewel.markdown.rendering.LocalMarkdownImageSourceResolver
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * Provide Markdown styling based on the current theme.
 *
 * By default, no code syntax highlighting will be available. If you do have a [codeHighlighter] instance to use
 * instead, you should provide it.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    imageSourceResolver: ImageSourceResolver,
    isDark: Boolean = JewelTheme.isDark,
    markdownStyling: MarkdownStyling =
        remember(JewelTheme.instanceUuid) {
            if (isDark) {
                MarkdownStyling.dark()
            } else {
                MarkdownStyling.light()
            }
        },
    markdownMode: MarkdownMode = MarkdownMode.Standalone,
    markdownProcessor: MarkdownProcessor = remember(markdownMode) { MarkdownProcessor(markdownMode = markdownMode) },
    markdownBlockRenderer: MarkdownBlockRenderer =
        remember(markdownStyling) {
            if (isDark) {
                MarkdownBlockRenderer.dark(markdownStyling)
            } else {
                MarkdownBlockRenderer.light(markdownStyling)
            }
        },
    codeHighlighter: CodeHighlighter = remember { NoOpCodeHighlighter },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMarkdownImageSourceResolver provides imageSourceResolver) {
        ProvideMarkdownStyling(
            isDark,
            markdownStyling,
            markdownMode,
            markdownProcessor,
            markdownBlockRenderer,
            codeHighlighter,
            content,
        )
    }
}

/**
 * Provide Markdown styling based on the current theme.
 *
 * By default, no code syntax highlighting will be available. If you do have a [codeHighlighter] instance to use
 * instead, you should provide it.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    isDark: Boolean = JewelTheme.isDark,
    markdownStyling: MarkdownStyling =
        remember(JewelTheme.instanceUuid) {
            if (isDark) {
                MarkdownStyling.dark()
            } else {
                MarkdownStyling.light()
            }
        },
    markdownMode: MarkdownMode = MarkdownMode.Standalone,
    markdownProcessor: MarkdownProcessor = remember(markdownMode) { MarkdownProcessor(markdownMode = markdownMode) },
    markdownBlockRenderer: MarkdownBlockRenderer =
        remember(markdownStyling) {
            if (isDark) {
                MarkdownBlockRenderer.dark(markdownStyling)
            } else {
                MarkdownBlockRenderer.light(markdownStyling)
            }
        },
    codeHighlighter: CodeHighlighter = remember { NoOpCodeHighlighter },
    content: @Composable () -> Unit,
) {
    ProvideMarkdownStyling(
        markdownStyling,
        markdownBlockRenderer,
        codeHighlighter,
        markdownMode,
        markdownProcessor,
        content,
    )
}

/** Provide Markdown styling based on the current theme. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    imageSourceResolver: ImageSourceResolver,
    markdownStyling: MarkdownStyling,
    markdownBlockRenderer: MarkdownBlockRenderer,
    codeHighlighter: CodeHighlighter,
    markdownMode: MarkdownMode = MarkdownMode.Standalone,
    markdownProcessor: MarkdownProcessor = remember(markdownMode) { MarkdownProcessor(markdownMode = markdownMode) },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMarkdownImageSourceResolver provides imageSourceResolver) {
        ProvideMarkdownStyling(
            markdownStyling,
            markdownBlockRenderer,
            codeHighlighter,
            markdownMode,
            markdownProcessor,
            content,
        )
    }
}

/** Provide Markdown styling based on the current theme. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    markdownStyling: MarkdownStyling,
    markdownBlockRenderer: MarkdownBlockRenderer,
    codeHighlighter: CodeHighlighter,
    markdownMode: MarkdownMode = MarkdownMode.Standalone,
    markdownProcessor: MarkdownProcessor = remember(markdownMode) { MarkdownProcessor(markdownMode = markdownMode) },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMarkdownStyling provides markdownStyling,
        LocalMarkdownProcessor provides markdownProcessor,
        LocalMarkdownBlockRenderer provides markdownBlockRenderer,
        LocalCodeHighlighter provides codeHighlighter,
    ) {
        content()
    }
}
