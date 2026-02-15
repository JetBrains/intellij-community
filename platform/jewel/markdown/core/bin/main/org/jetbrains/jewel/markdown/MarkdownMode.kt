// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownMode
import org.jetbrains.jewel.markdown.scrolling.ScrollingSynchronizer

/**
 * Indicates possible scenarios of how markdown files are presented:
 * - [Standalone] mode is the default scenario;
 * - [EditorPreview] mode is intended for cases when the raw file can be edited, and changes are expected to affect
 *   rendered contents immediately.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface MarkdownMode {
    /** Default mode when only rendered contents of a file is shown to a user. */
    @ApiStatus.Experimental @ExperimentalJewelApi public object Standalone : MarkdownMode

    /**
     * Mode that is intended for cases when the raw file can be edited, and changes are expected to affect rendered
     * contents immediately.
     *
     * @param scrollingSynchronizer [ScrollingSynchronizer] that enables auto-scrolling in the preview to match the
     *   scrolling position in the editor and therefore show the same blocks that are currently visible in the editor.
     */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public class EditorPreview(public val scrollingSynchronizer: ScrollingSynchronizer?) : MarkdownMode
}

@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun WithMarkdownMode(mode: MarkdownMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMarkdownMode provides mode) { content() }
}
