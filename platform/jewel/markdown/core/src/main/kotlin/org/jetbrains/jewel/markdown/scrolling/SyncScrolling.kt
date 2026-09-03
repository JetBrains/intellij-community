// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Keeps [editorScrollState] and this synchronizer's preview in step, in both directions, until the calling coroutine is
 * canceled.
 *
 * [editorOffsetOfLine] is asked for the editor's line positions on every scroll, so it can follow the editor's text
 * layout as it changes. For an editor that is a Compose text field, see [sourceLineOffsets].
 *
 * Whichever pane the user is scrolling themselves wins: a gesture holds that pane's scroll mutex at
 * [MutatePriority.UserInput][androidx.compose.foundation.MutatePriority.UserInput], and syncing only asks at `Default`,
 * so it is refused while the gesture lasts and resyncs on the next scroll. Anything else the host scrolls these panes
 * with, such as a caret-driven [ScrollingSynchronizer.scrollToLine], competes at `Default` and can be refused the same
 * way.
 *
 * @param editorScrollState The scroll state of the editor pane.
 * @param editorOffsetOfLine Supplies the editor's current line positions; it is asked again on every scroll. See
 *   [ContinuousScrollingSynchronizer] for what the function it returns means.
 * @see ScrollingSynchronizer.createContinuous
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public suspend fun ContinuousScrollingSynchronizer.syncScrolling(
    editorScrollState: ScrollState,
    editorOffsetOfLine: () -> (Int) -> Int?,
) {
    // Syncing one pane moves the other, which would sync the first one back forever. Rather than trying to recognize
    // that echo -- which can't be told apart from the user scrolling back to the same spot, and which snapshotFlow's
    // conflation would let through anyway -- neither direction moves a pane that is already where it belongs.
    coroutineScope {
        launch {
            // Reading the preview's position through editorOffsetAtPreview is what makes this re-emit when the preview
            // moves, without the preview having to expose an offset a lazy list wouldn't have.
            snapshotFlow { editorOffsetAtPreview(editorScrollState.maxValue, editorOffsetOfLine()) }
                .collect { previewAsEditorOffset ->
                    val editorOffset = editorScrollState.value
                    if (inSync(previewAsEditorOffset, editorOffset)) return@collect
                    unlessTheUserIsScrolling { editorScrollState.scrollTo(previewAsEditorOffset) }
                }
        }
        launch {
            snapshotFlow { editorScrollState.value }
                .collect { editorOffset ->
                    val editorMaxOffset = editorScrollState.maxValue
                    val lineOffsets = editorOffsetOfLine()
                    val previewAsEditorOffset = editorOffsetAtPreview(editorMaxOffset, lineOffsets)
                    if (inSync(previewAsEditorOffset, editorOffset)) return@collect
                    unlessTheUserIsScrolling { scrollPreviewTo(editorOffset, editorMaxOffset, lineOffsets) }
                }
        }
    }
}

/**
 * Runs [scroll], unless the pane it moves is one the user is scrolling themselves: a gesture holds that pane's scroll
 * mutex at `UserInput`, and `MutatorMutex` refuses a lower priority caller by throwing rather than queueing. Real
 * cancellation of [syncScrolling] still propagates.
 */
private suspend inline fun unlessTheUserIsScrolling(scroll: () -> Unit) {
    try {
        scroll()
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/** Whether the panes already agree, both arguments being editor scroll offsets, so that neither has to move. */
private fun inSync(a: Int, b: Int) = abs(a - b) <= SYNC_TOLERANCE

// Mapping an offset onto the other pane and back rounds once in each direction, and the second rounding is scaled by
// the ratio between the two stretches, so a round trip can land a pixel or two off. Anything below that would let the
// two panes push each other back and forth forever.
private const val SYNC_TOLERANCE = 2

/**
 * The scroll offset at which each source line starts in this layout, for an editor that is a Compose text field
 * scrolled together with its text. Pass the result to [syncScrolling] or to
 * [ContinuousScrollingSynchronizer.scrollPreviewTo], and compute it again whenever the layout changes.
 *
 * @return A function giving the scroll offset at which a source line starts, or `null` for a line this layout does not
 *   contain.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public fun TextLayoutResult.sourceLineOffsets(): (Int) -> Int? {
    val lineStarts = buildList {
        add(0)
        LINE_TERMINATOR.findAll(layoutInput.text).mapTo(this) { it.range.last + 1 }
    }
    return { line -> lineStarts.getOrNull(line)?.let { getLineTop(getLineForOffset(it)).roundToInt() } }
}

private val LINE_TERMINATOR = Regex("\r\n|[\n\r]")
