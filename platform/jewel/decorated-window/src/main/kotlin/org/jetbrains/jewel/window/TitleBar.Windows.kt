package org.jetbrains.jewel.window

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations.CustomTitleBar
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.util.isDark
import org.jetbrains.jewel.window.styling.TitleBarStyle

@Composable
internal fun DecoratedWindowScope.TitleBarOnWindows(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = JewelTheme.defaultTitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit,
) {
    val titleBar = remember { JBR.getWindowDecorations().createCustomTitleBar() }

    TitleBarImpl(
        modifier = modifier,
        gradientStartColor = gradientStartColor,
        style = style,
        applyTitleBar = { height, _ ->
            titleBar.height = height.value
            titleBar.putProperty("controls.dark", style.colors.background.isDark())
            JBR.getWindowDecorations().setCustomTitleBar(window, titleBar)
            PaddingValues(start = titleBar.leftInset.dp, end = titleBar.rightInset.dp)
        },
        backgroundContent = { Spacer(modifier = modifier.fillMaxSize().customTitleBarMouseEventHandler(titleBar)) },
        content = content,
    )
}

internal fun Modifier.customTitleBarMouseEventHandler(titleBar: CustomTitleBar): Modifier =
    pointerInput(titleBar) {
        val currentContext = currentCoroutineContext()
        awaitPointerEventScope {
            var inUserControl = false
            while (currentContext.isActive) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach {
                    if (!it.isConsumed && !inUserControl) {
                        titleBar.forceHitTest(false)
                    } else {
                        if (event.type == PointerEventType.Press) {
                            inUserControl = true
                        }
                        if (event.type == PointerEventType.Release) {
                            inUserControl = false
                        }
                        titleBar.forceHitTest(true)
                    }
                }
            }
        }
    }
