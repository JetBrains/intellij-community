// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * A [ScrollingSynchronizer] that can also follow the editor continuously, instead of jumping from block to block the
 * way [scrollToLine][ScrollingSynchronizer.scrollToLine] does.
 *
 * The editor is a single [androidx.compose.foundation.ScrollState], so it is described here by a scroll offset. The
 * preview is not: it may be a scrolled column, or a lazy list that has no absolute scroll offset at all. That is why
 * this class never hands out a preview offset, and instead scrolls the preview itself.
 *
 * Use [syncScrolling] to keep an editor and a preview in step in both directions.
 *
 * Both members below take the same two descriptions of the editor: `editorMaxOffset`, its maximum scroll offset, paired
 * with the preview's own so that both panes reach their ends together; and `editorOffsetOfLine`, returning the editor
 * scroll offset at which a source line starts, or `null` if the editor doesn't know that line.
 *
 * @see ScrollingSynchronizer.createContinuous
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public abstract class ContinuousScrollingSynchronizer : ScrollingSynchronizer() {
    /**
     * Scroll the preview to the position matching the editor scroll offset [editorOffset], interpolated between the
     * tops of the two blocks around it, so that the preview keeps moving as long as the editor does.
     */
    public abstract suspend fun scrollPreviewTo(
        editorOffset: Int,
        editorMaxOffset: Int,
        editorOffsetOfLine: (Int) -> Int?,
    )

    /**
     * The editor scroll offset matching where the preview currently sits. The inverse of [scrollPreviewTo].
     *
     * This reads the preview's scroll position from the current snapshot, so callers can observe it with
     * `snapshotFlow`; see [syncScrolling].
     */
    public abstract fun editorOffsetAtPreview(editorMaxOffset: Int, editorOffsetOfLine: (Int) -> Int?): Int
}
