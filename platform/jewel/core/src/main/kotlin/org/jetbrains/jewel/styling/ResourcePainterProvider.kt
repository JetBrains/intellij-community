package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.SvgPatcher

@Immutable
abstract class ResourcePainterProvider<T : InteractiveComponentState> internal constructor(
    svgPatcher: SvgPatcher,
) : BaseResourcePainterProvider<T>(svgPatcher) {

    @Composable
    override fun selectVariant(state: T): String =
        state.chooseValue(normal, disabled, focused, pressed, hovered)

    companion object {

        @Composable
        fun <T : InteractiveComponentState> create(
            normal: String,
            disabled: String,
            focused: String,
            pressed: String,
            hovered: String,
            svgPatcher: SvgPatcher,
        ) = object : ResourcePainterProvider<T>(svgPatcher) {
            override val normal = normal
            override val disabled = disabled
            override val focused = focused
            override val pressed = pressed
            override val hovered = hovered
        }
    }
}
