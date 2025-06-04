// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.intui.markdown.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.jewel.bridge.code.highlighting.CodeHighlighterFactory
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.code.highlighting.CodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.markdown.MarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownMode
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * Provide Markdown styling, for scenarios where you do not have access to a [Project].
 *
 * By default, this means no code syntax highlighting will be available. If you do have a [codeHighlighter] instance to
 * use instead, you should provide it. If you have access to a [Project], you should be using the other
 * [ProvideMarkdownStyling] overload instead, as that will provide syntax highlighting by default.
 */
@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    markdownStyling: MarkdownStyling = remember(JewelTheme.instanceUuid) { MarkdownStyling.create() },
    markdownMode: MarkdownMode = MarkdownMode.Standalone,
    markdownProcessor: MarkdownProcessor = remember(markdownMode) { MarkdownProcessor(markdownMode = markdownMode) },
    markdownBlockRenderer: MarkdownBlockRenderer =
        remember(markdownStyling) { MarkdownBlockRenderer.create(markdownStyling) },
    codeHighlighter: CodeHighlighter = remember { NoOpCodeHighlighter },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalMarkdownStyling provides markdownStyling,
        LocalMarkdownMode provides markdownMode,
        LocalMarkdownProcessor provides markdownProcessor,
        LocalMarkdownBlockRenderer provides markdownBlockRenderer,
        LocalCodeHighlighter provides codeHighlighter,
    ) {
        content()
    }
}

/**
 * Provide Markdown styling, for scenarios where you have access to a [Project].
 *
 * The [project] is used to access the [CodeHighlighterFactory] and obtain a [CodeHighlighter] that supports code syntax
 * highlighting.
 */
@ExperimentalJewelApi
@Composable
public fun ProvideMarkdownStyling(
    project: Project,
    markdownStyling: MarkdownStyling = remember(JewelTheme.instanceUuid) { MarkdownStyling.create() },
    markdownMode: MarkdownMode = MarkdownMode.Standalone,
    markdownProcessor: MarkdownProcessor = remember(markdownMode) { MarkdownProcessor(markdownMode = markdownMode) },
    markdownBlockRenderer: MarkdownBlockRenderer =
        remember(markdownStyling) { MarkdownBlockRenderer.create(markdownStyling) },
    content: @Composable () -> Unit,
) {
    val codeHighlighter = remember { project.service<CodeHighlighterFactory>().createHighlighter() }

    ProvideMarkdownStyling(
        markdownStyling = markdownStyling,
        markdownMode = markdownMode,
        markdownProcessor = markdownProcessor,
        markdownBlockRenderer = markdownBlockRenderer,
        codeHighlighter = codeHighlighter,
        content = content,
    )
}
