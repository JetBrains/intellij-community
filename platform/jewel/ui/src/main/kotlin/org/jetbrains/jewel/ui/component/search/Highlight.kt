// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.ui.component.LocalNodeSearchMatchState
import org.jetbrains.jewel.ui.component.NodeSearchMatchState

@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun CharSequence.highlightTextSearch(
    matchState: NodeSearchMatchState? = LocalNodeSearchMatchState.current
): AnnotatedString = buildAnnotatedString {
    append(this@highlightTextSearch)

    val matchResult = matchState?.matchResult
    if (matchResult !is SpeedSearchMatcher.MatchResult.Match) return@buildAnnotatedString

    for (match in matchResult.ranges) {
        // We need to add 1 to the last index to fill the last letter
        addStyle(SpanStyle(color = matchState.style.colors.foreground), match.first, match.last + 1)
    }
}

@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun Modifier.highlightSpeedSearchMatches(
    textLayoutResult: TextLayoutResult?,
    matchState: NodeSearchMatchState? = LocalNodeSearchMatchState.current,
): Modifier {
    val matchResult = matchState?.matchResult
    if (matchResult !is SpeedSearchMatcher.MatchResult.Match || textLayoutResult == null) return this

    val style = matchState.style

    return drawBehind {
        val topPadding = style.metrics.padding.calculateTopPadding().toPx()
        val bottomPadding = style.metrics.padding.calculateBottomPadding().toPx()

        for (match in matchResult.ranges) {
            val matchingBoxes = match.map { textLayoutResult.getBoundingBox(it) }
            val matchingRects = matchingBoxes.groupBy { it.top }.values

            for (lineRects in matchingRects) {
                val mergedRect =
                    lineRects
                        .reduce { acc, rect ->
                            acc.copy(
                                left = minOf(acc.left, rect.left),
                                top = minOf(acc.top, rect.top),
                                right = maxOf(acc.right, rect.right),
                                bottom = maxOf(acc.bottom, rect.bottom),
                            )
                        }
                        .let { it.copy(top = it.top - topPadding, bottom = it.bottom + bottomPadding) }

                drawRoundRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(style.colors.startBackground, style.colors.endBackground)
                        ),
                    topLeft = mergedRect.topLeft,
                    size = mergedRect.size,
                    cornerRadius = CornerRadius(style.metrics.cornerSize.toPx(mergedRect.size, this)),
                )
            }
        }
    }
}
