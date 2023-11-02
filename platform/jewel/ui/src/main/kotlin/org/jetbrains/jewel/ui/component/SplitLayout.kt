package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import java.awt.Cursor
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.Orientation as ComposeOrientation

@Composable
public fun HorizontalSplitLayout(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    dividerColor: Color = JewelTheme.globalColors.borders.normal,
    dividerThickness: Dp = 1.dp,
    dividerIndent: Dp = 0.dp,
    draggableWidth: Dp = 8.dp,
    minRatio: Float = 0f,
    maxRatio: Float = 1f,
    initialDividerPosition: Dp = 300.dp,
) {
    val density = LocalDensity.current
    var dividerX by remember { mutableStateOf(with(density) { initialDividerPosition.roundToPx() }) }

    Layout(
        modifier = modifier,
        content = {
            val dividerInteractionSource = remember { MutableInteractionSource() }
            first(Modifier.layoutId("first"))

            Divider(
                orientation = Orientation.Vertical,
                modifier = Modifier.fillMaxHeight().layoutId("divider"),
                color = dividerColor,
                thickness = dividerThickness,
                startIndent = dividerIndent,
            )

            second(Modifier.layoutId("second"))

            Box(
                Modifier.fillMaxHeight()
                    .width(draggableWidth)
                    .draggable(
                        interactionSource = dividerInteractionSource,
                        orientation = ComposeOrientation.Horizontal,
                        state = rememberDraggableState { delta -> dividerX += delta.toInt() },
                    )
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                    .layoutId("divider-handle"),
            )
        },
    ) { measurables, incomingConstraints ->
        val availableWidth = incomingConstraints.maxWidth
        val actualDividerX = dividerX.coerceIn(0, availableWidth)
            .coerceIn(
                (availableWidth * minRatio).roundToInt(),
                (availableWidth * maxRatio).roundToInt(),
            )

        val dividerMeasurable = measurables.single { it.layoutId == "divider" }
        val dividerPlaceable =
            dividerMeasurable.measure(
                Constraints.fixed(dividerThickness.roundToPx(), incomingConstraints.maxHeight),
            )

        val firstComponentConstraints =
            Constraints.fixed((actualDividerX).coerceAtLeast(0), incomingConstraints.maxHeight)
        val firstPlaceable = measurables.find { it.layoutId == "first" }
            ?.measure(firstComponentConstraints)
            ?: error("No first component found. Have you applied the provided Modifier to it?")

        val secondComponentConstraints =
            Constraints.fixed(
                width = availableWidth - actualDividerX + dividerPlaceable.width,
                height = incomingConstraints.maxHeight,
            )
        val secondPlaceable = measurables.find { it.layoutId == "second" }
            ?.measure(secondComponentConstraints)
            ?: error("No second component found. Have you applied the provided Modifier to it?")

        val dividerHandlePlaceable =
            measurables.single { it.layoutId == "divider-handle" }
                .measure(Constraints.fixedHeight(incomingConstraints.maxHeight))

        layout(availableWidth, incomingConstraints.maxHeight) {
            firstPlaceable.placeRelative(0, 0)
            dividerPlaceable.placeRelative(actualDividerX - dividerPlaceable.width / 2, 0)
            secondPlaceable.placeRelative(actualDividerX + dividerPlaceable.width, 0)
            dividerHandlePlaceable.placeRelative(actualDividerX - dividerHandlePlaceable.measuredWidth / 2, 0)
        }
    }
}

@Composable
public fun VerticalSplitLayout(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    dividerColor: Color = JewelTheme.globalColors.borders.normal,
    dividerThickness: Dp = 1.dp,
    dividerIndent: Dp = 0.dp,
    draggableWidth: Dp = 8.dp,
    minRatio: Float = 0f,
    maxRatio: Float = 1f,
    initialDividerPosition: Dp = 300.dp,
) {
    val density = LocalDensity.current
    var dividerY by remember { mutableStateOf(with(density) { initialDividerPosition.roundToPx() }) }

    Layout(
        modifier = modifier,
        content = {
            val dividerInteractionSource = remember { MutableInteractionSource() }
            first(Modifier.layoutId("first"))

            Divider(
                orientation = Orientation.Horizontal,
                modifier = Modifier.fillMaxHeight().layoutId("divider"),
                color = dividerColor,
                thickness = dividerThickness,
                startIndent = dividerIndent,
            )

            second(Modifier.layoutId("second"))

            Box(
                Modifier.fillMaxWidth()
                    .height(draggableWidth)
                    .draggable(
                        interactionSource = dividerInteractionSource,
                        orientation = ComposeOrientation.Vertical,
                        state = rememberDraggableState { delta -> dividerY += delta.toInt() },
                    )
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                    .layoutId("divider-handle"),
            )
        },
    ) { measurables, incomingConstraints ->
        val availableHeight = incomingConstraints.maxHeight
        val actualDividerY = dividerY.coerceIn(0, availableHeight)
            .coerceIn(
                (availableHeight * minRatio).roundToInt(),
                (availableHeight * maxRatio).roundToInt(),
            )

        val dividerMeasurable = measurables.single { it.layoutId == "divider" }
        val dividerPlaceable =
            dividerMeasurable.measure(
                Constraints.fixed(incomingConstraints.maxWidth, dividerThickness.roundToPx()),
            )

        val firstComponentConstraints =
            Constraints.fixed(incomingConstraints.maxWidth, (actualDividerY - 1).coerceAtLeast(0))
        val firstPlaceable = measurables.find { it.layoutId == "first" }
            ?.measure(firstComponentConstraints)
            ?: error("No first component found. Have you applied the provided Modifier to it?")

        val secondComponentConstraints =
            Constraints.fixed(
                width = incomingConstraints.maxWidth,
                height = availableHeight - actualDividerY + dividerPlaceable.height,
            )
        val secondPlaceable = measurables.find { it.layoutId == "second" }
            ?.measure(secondComponentConstraints)
            ?: error("No second component found. Have you applied the provided Modifier to it?")

        val dividerHandlePlaceable = measurables.single { it.layoutId == "divider-handle" }
            .measure(Constraints.fixedWidth(incomingConstraints.maxWidth))

        layout(incomingConstraints.maxWidth, availableHeight) {
            firstPlaceable.placeRelative(0, 0)
            dividerPlaceable.placeRelative(0, actualDividerY - dividerPlaceable.height / 2)
            secondPlaceable.placeRelative(0, actualDividerY + dividerPlaceable.height)
            dividerHandlePlaceable.placeRelative(0, actualDividerY - dividerHandlePlaceable.measuredHeight / 2)
        }
    }
}
