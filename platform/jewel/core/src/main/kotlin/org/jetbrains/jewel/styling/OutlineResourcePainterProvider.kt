package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.StateWithOutline
import org.jetbrains.jewel.SvgPatcher

@Immutable
abstract class OutlineResourcePainterProvider<T : StateWithOutline> internal constructor(
    svgPatcher: SvgPatcher,
) : BaseResourcePainterProvider<T>(svgPatcher) {

    abstract val warning: String
    abstract val error: String

    @Composable
    override fun selectVariant(state: T): String =
        state.chooseValueWithOutline(normal, disabled, focused, pressed, hovered, warning, error)

    companion object {

        @Composable
        fun <T : StateWithOutline> create(
            normal: String,
            disabled: String,
            focused: String,
            pressed: String,
            hovered: String,
            warning: String,
            error: String,
            svgPatcher: SvgPatcher,
        ) = object : OutlineResourcePainterProvider<T>(svgPatcher) {
            override val normal = normal
            override val disabled = disabled
            override val focused = focused
            override val pressed = pressed
            override val hovered = hovered
            override val warning = warning
            override val error = error
        }
    }
}
