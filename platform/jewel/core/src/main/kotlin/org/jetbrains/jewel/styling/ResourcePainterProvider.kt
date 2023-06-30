package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.painterResource
import org.jetbrains.jewel.InteractiveComponentState

@Immutable
abstract class ResourcePainterProvider<T : InteractiveComponentState> : StatefulPainterProvider<T> {

    abstract val normal: String
    abstract val disabled: String
    abstract val focused: String
    abstract val pressed: String
    abstract val hovered: String

    @Composable
    override fun getPainter(state: T, resourceLoader: ResourceLoader): State<Painter> {
        val iconPath = state.chooseValue(normal, disabled, focused, pressed, hovered)
        return rememberUpdatedState(painterResource(iconPath, resourceLoader))
    }
}
