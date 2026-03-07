package org.jetbrains.jewel.window

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.util.isDark
import org.jetbrains.jewel.window.styling.TitleBarStyle
import org.jetbrains.jewel.window.utils.WindowMouseEventEffect

@Composable
internal fun DecoratedWindowScope.TitleBarOnWindows(
    modifier: Modifier = Modifier,
    gradientStartColor: Color = Color.Unspecified,
    style: TitleBarStyle = JewelTheme.defaultTitleBarStyle,
    content: @Composable TitleBarScope.(DecoratedWindowState) -> Unit,
) {
    val titleBar = remember { JBR.getWindowDecorations().createCustomTitleBar() }

    WindowMouseEventEffect(titleBar)

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
        backgroundContent = { Spacer(modifier = Modifier.fillMaxSize()) },
        content = content,
    )
}
