package org.jetbrains.jewel.window

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.jetbrains.JBR
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.modifier.trackWindowActivation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle
import org.jetbrains.jewel.window.utils.DesktopPlatform

@Suppress("ModifierMissing")
@Composable
public fun DecoratedWindow(
    onCloseRequest: () -> Unit,
    state: WindowState = rememberWindowState(),
    visible: Boolean = true,
    title: String = "",
    icon: Painter? = null,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    style: DecoratedWindowStyle = JewelTheme.defaultDecoratedWindowStyle,
    content: @Composable DecoratedWindowScope.() -> Unit,
) {
    remember {
        if (!JBR.isAvailable()) {
            error(
                "DecoratedWindow can only be used on JetBrainsRuntime(JBR) platform, " +
                    "please check the document https://github.com/JetBrains/jewel#int-ui-standalone-theme"
            )
        }
    }

    // Using undecorated window for linux
    val undecorated = DesktopPlatform.Linux == DesktopPlatform.Current

    Window(
        onCloseRequest,
        state,
        visible,
        title,
        icon,
        undecorated,
        transparent = false,
        resizable,
        enabled,
        focusable,
        alwaysOnTop,
        onPreviewKeyEvent,
        onKeyEvent,
    ) {
        var decoratedWindowState by remember { mutableStateOf(DecoratedWindowState.of(window)) }

        DisposableEffect(window) {
            val adapter =
                object : WindowAdapter(), ComponentListener {
                    override fun windowActivated(e: WindowEvent?) {
                        decoratedWindowState = DecoratedWindowState.of(window)
                    }

                    override fun windowDeactivated(e: WindowEvent?) {
                        decoratedWindowState = DecoratedWindowState.of(window)
                    }

                    override fun windowIconified(e: WindowEvent?) {
                        decoratedWindowState = DecoratedWindowState.of(window)
                    }

                    override fun windowDeiconified(e: WindowEvent?) {
                        decoratedWindowState = DecoratedWindowState.of(window)
                    }

                    override fun windowStateChanged(e: WindowEvent) {
                        decoratedWindowState = DecoratedWindowState.of(window)
                    }

                    override fun componentResized(e: ComponentEvent?) {
                        decoratedWindowState = DecoratedWindowState.of(window)
                    }

                    override fun componentMoved(e: ComponentEvent?) {
                        // Empty
                    }

                    override fun componentShown(e: ComponentEvent?) {
                        // Empty
                    }

                    override fun componentHidden(e: ComponentEvent?) {
                        // Empty
                    }
                }

            window.addWindowListener(adapter)
            window.addWindowStateListener(adapter)
            window.addComponentListener(adapter)

            onDispose {
                window.removeWindowListener(adapter)
                window.removeWindowStateListener(adapter)
                window.removeComponentListener(adapter)
            }
        }

        val undecoratedWindowBorder =
            if (undecorated && !decoratedWindowState.isMaximized) {
                Modifier.border(
                        Stroke.Alignment.Inside,
                        style.metrics.borderWidth,
                        style.colors.borderFor(decoratedWindowState).value,
                        RectangleShape,
                    )
                    .padding(style.metrics.borderWidth)
            } else {
                Modifier
            }

        CompositionLocalProvider(LocalTitleBarInfo provides TitleBarInfo(title, icon)) {
            Layout(
                content = {
                    val scope =
                        object : DecoratedWindowScope {
                            override val state: DecoratedWindowState
                                get() = decoratedWindowState

                            override val window: ComposeWindow
                                get() = this@Window.window
                        }
                    scope.content()
                },
                modifier = undecoratedWindowBorder.trackWindowActivation(window),
                measurePolicy = DecoratedWindowMeasurePolicy,
            )
        }
    }
}

@Stable
public interface DecoratedWindowScope : FrameWindowScope {
    override val window: ComposeWindow

    public val state: DecoratedWindowState
}

private object DecoratedWindowMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        if (measurables.isEmpty()) {
            return layout(width = constraints.minWidth, height = constraints.minHeight) {}
        }

        val titleBars = measurables.filter { it.layoutId == TITLE_BAR_LAYOUT_ID }
        if (titleBars.size > 1) {
            error("Window just can have only one title bar")
        }
        val titleBar = titleBars.firstOrNull()
        val titleBarBorder = measurables.firstOrNull { it.layoutId == TITLE_BAR_BORDER_LAYOUT_ID }

        val contentConstraints = constraints.copy(minWidth = 0, minHeight = 0)

        val titleBarPlaceable = titleBar?.measure(contentConstraints)
        val titleBarHeight = titleBarPlaceable?.height ?: 0

        val titleBarBorderPlaceable = titleBarBorder?.measure(contentConstraints)
        val titleBarBorderHeight = titleBarBorderPlaceable?.height ?: 0

        val measuredPlaceable = mutableListOf<Placeable>()

        for (it in measurables) {
            if (it.layoutId.toString().startsWith(TITLE_BAR_COMPONENT_LAYOUT_ID_PREFIX)) continue
            val offsetConstraints = contentConstraints.offset(vertical = -titleBarHeight - titleBarBorderHeight)
            val placeable = it.measure(offsetConstraints)
            measuredPlaceable += placeable
        }

        return layout(constraints.maxWidth, constraints.maxHeight) {
            titleBarPlaceable?.placeRelative(0, 0)
            titleBarBorderPlaceable?.placeRelative(0, titleBarHeight)

            measuredPlaceable.forEach { it.placeRelative(0, titleBarHeight + titleBarBorderHeight) }
        }
    }
}

@Immutable
@JvmInline
public value class DecoratedWindowState(public val state: ULong) {
    public val isActive: Boolean
        get() = state and Active != 0UL

    public val isFullscreen: Boolean
        get() = state and Fullscreen != 0UL

    public val isMinimized: Boolean
        get() = state and Minimize != 0UL

    public val isMaximized: Boolean
        get() = state and Maximize != 0UL

    public fun copy(
        fullscreen: Boolean = isFullscreen,
        minimized: Boolean = isMinimized,
        maximized: Boolean = isMaximized,
        active: Boolean = isActive,
    ): DecoratedWindowState = of(fullscreen = fullscreen, minimized = minimized, maximized = maximized, active = active)

    override fun toString(): String = "${javaClass.simpleName}(isFullscreen=$isFullscreen, isActive=$isActive)"

    public companion object {
        public val Active: ULong = 1UL shl 0
        public val Fullscreen: ULong = 1UL shl 1
        public val Minimize: ULong = 1UL shl 2
        public val Maximize: ULong = 1UL shl 3

        public fun of(
            fullscreen: Boolean = false,
            minimized: Boolean = false,
            maximized: Boolean = false,
            active: Boolean = true,
        ): DecoratedWindowState =
            DecoratedWindowState(
                (if (fullscreen) Fullscreen else 0UL) or
                    (if (minimized) Minimize else 0UL) or
                    (if (maximized) Maximize else 0UL) or
                    (if (active) Active else 0UL)
            )

        public fun of(window: ComposeWindow): DecoratedWindowState =
            of(
                fullscreen = window.placement == WindowPlacement.Fullscreen,
                minimized = window.isMinimized,
                maximized = window.placement == WindowPlacement.Maximized,
                active = window.isActive,
            )
    }
}

internal data class TitleBarInfo(val title: String, val icon: Painter?)

internal val LocalTitleBarInfo: ProvidableCompositionLocal<TitleBarInfo> = compositionLocalOf {
    error("LocalTitleBarInfo not provided, TitleBar must be used in DecoratedWindow")
}
