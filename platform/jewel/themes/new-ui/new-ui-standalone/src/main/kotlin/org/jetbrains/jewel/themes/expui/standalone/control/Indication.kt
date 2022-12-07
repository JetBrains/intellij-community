@file:Suppress("MatchingDeclarationName")
package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors

class HoverOrPressedIndication(private val shape: Shape) : Indication {
    private class IndicationInstance(
        private val shape: Shape,
        private val isHover: State<Boolean>,
        private val isPressed: State<Boolean>,
        private val hoverColor: Color,
        private val pressedColor: Color,
    ) : androidx.compose.foundation.IndicationInstance {

        override fun ContentDrawScope.drawIndication() {
            when {
                isPressed.value -> {
                    if (pressedColor.isSpecified) {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        drawOutline(outline, pressedColor)
                    }
                }

                isHover.value -> {
                    if (hoverColor.isSpecified) {
                        val outline = shape.createOutline(size, layoutDirection, this)
                        drawOutline(outline, hoverColor)
                    }
                }
            }
            drawContent()
        }
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): androidx.compose.foundation.IndicationInstance {
        val hoverColors = LocalHoverAreaColors.current
        val pressedColors = LocalPressedAreaColors.current
        val isPressed = interactionSource.collectIsPressedAsState()
        val isHover = interactionSource.collectIsHoveredAsState()

        return remember(hoverColors, pressedColors, interactionSource) {
            IndicationInstance(
                shape, isHover, isPressed, hoverColors.startBackground, pressedColors.startBackground
            )
        }
    }
}
