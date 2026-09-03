// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Keeps [editorScrollState] and [previewScrollState] in step, in both directions, until the calling coroutine is
 * canceled. [previewScrollState] must be the one this synchronizer was created with.
 *
 * [editorOffsetOfLine] is asked for the editor's line positions on every scroll, so it can follow the editor's text
 * layout as it changes. For an editor that is a Compose text field, see [sourceLineOffsets].
 *
 * @see ScrollingSynchronizer.previewOffsetAt
 * @see ScrollingSynchronizer.editorOffsetAt
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public suspend fun ScrollingSynchronizer.syncScrolling(
    editorScrollState: ScrollState,
    previewScrollState: ScrollState,
    editorOffsetOfLine: () -> (Int) -> Int?,
) {
    coroutineScope {
        // Syncing one pane moves the other, which would sync the first one back.
        var expectedEditorOffset = NO_OFFSET
        var expectedPreviewOffset = NO_OFFSET
        launch {
            snapshotFlow { editorScrollState.value }
                .collect { offset ->
                    if (offset == expectedEditorOffset) {
                        expectedEditorOffset = NO_OFFSET
                        return@collect
                    }
                    val editorMaxOffset = editorScrollState.maxValue
                    expectedPreviewOffset =
                        previewScrollState.scrollToAndReport(
                            previewOffsetAt(offset, editorMaxOffset, editorOffsetOfLine())
                        )
                }
        }
        launch {
            snapshotFlow { previewScrollState.value }
                .collect { offset ->
                    if (offset == expectedPreviewOffset) {
                        expectedPreviewOffset = NO_OFFSET
                        return@collect
                    }
                    val editorMaxOffset = editorScrollState.maxValue
                    expectedEditorOffset =
                        editorScrollState.scrollToAndReport(
                            editorOffsetAt(offset, editorMaxOffset, editorOffsetOfLine())
                        )
                }
        }
    }
}

private const val NO_OFFSET = -1

/** Scrolls to [target] and returns the offset this state will report as a result, or [NO_OFFSET] if it didn't move. */
private suspend fun ScrollState.scrollToAndReport(target: Int): Int {
    val before = value
    scrollTo(target)
    return if (value != before) value else NO_OFFSET
}

/**
 * The scroll offset at which each source line starts in this layout, for an editor that is a Compose text field
 * scrolled together with its text. Pass the result to [syncScrolling], [ScrollingSynchronizer.previewOffsetAt] or
 * [ScrollingSynchronizer.editorOffsetAt], and compute it again whenever the layout changes.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun TextLayoutResult.sourceLineOffsets(): (Int) -> Int? {
    val lineStarts = buildList {
        add(0)
        layoutInput.text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }
    return { line -> lineStarts.getOrNull(line)?.let { getLineTop(getLineForOffset(it)).roundToInt() } }
}
