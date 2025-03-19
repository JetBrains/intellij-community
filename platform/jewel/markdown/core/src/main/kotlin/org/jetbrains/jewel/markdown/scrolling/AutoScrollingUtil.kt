// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.scrolling

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import kotlin.math.abs
import org.jetbrains.jewel.markdown.MarkdownBlock

/**
 * Use this composable as a wrapper to an actual block composable to enable scrolling to the block in an editor+preview
 * combined mode with scrolling synchronization.
 *
 * @see [ScrollSyncMarkdownBlockRenderer]
 */
@Composable
public fun AutoScrollableBlock(
    block: MarkdownBlock,
    synchronizer: ScrollingSynchronizer,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var previousPosition by remember(block) { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            modifier.onGloballyPositioned { coordinates ->
                val newPosition = coordinates.positionInRoot()
                if (abs(previousPosition.y - newPosition.y) > 1.0) {
                    previousPosition = newPosition
                    synchronizer.acceptGlobalPosition(block, coordinates)
                }
            }
    ) {
        content()
    }
}
