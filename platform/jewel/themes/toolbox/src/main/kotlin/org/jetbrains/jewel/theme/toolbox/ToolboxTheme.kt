package org.jetbrains.jewel.theme.toolbox

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.jewel.NoIndication
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.theme.toolbox.styles.ButtonStyle
import org.jetbrains.jewel.theme.toolbox.styles.CheckboxStyle
import org.jetbrains.jewel.theme.toolbox.styles.DividerStyle
import org.jetbrains.jewel.theme.toolbox.styles.FrameStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalButtonStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalCheckboxStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalDividerStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalFrameStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalIconButtonStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalProgressIndicatorStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalRadioButtonStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalSwitchStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalTabStyle
import org.jetbrains.jewel.theme.toolbox.styles.LocalTextFieldStyle
import org.jetbrains.jewel.theme.toolbox.styles.ProgressIndicatorStyle
import org.jetbrains.jewel.theme.toolbox.styles.RadioButtonStyle
import org.jetbrains.jewel.theme.toolbox.styles.ScrollbarStyle
import org.jetbrains.jewel.theme.toolbox.styles.SwitchStyle
import org.jetbrains.jewel.theme.toolbox.styles.TabStyle
import org.jetbrains.jewel.theme.toolbox.styles.TextFieldStyle

@Composable
fun ToolboxTheme(
    palette: Palette,
    metrics: ToolboxMetrics,
    typography: ToolboxTypography,
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalMetrics provides metrics,
    LocalTypography provides typography,
    LocalFrameStyle provides FrameStyle(palette),
    LocalTextStyle provides typography.body,
    LocalButtonStyle provides ButtonStyle(palette, metrics, typography),
    LocalIconButtonStyle provides ButtonStyle(palette, metrics, typography),
    LocalSwitchStyle provides SwitchStyle(palette, metrics),
    LocalCheckboxStyle provides CheckboxStyle(palette, metrics),
    LocalRadioButtonStyle provides RadioButtonStyle(palette, metrics),
    LocalTextFieldStyle provides TextFieldStyle(palette, metrics, typography),
    LocalDividerStyle provides DividerStyle(palette),
    LocalTabStyle provides TabStyle(palette, metrics, typography),
    LocalProgressIndicatorStyle provides ProgressIndicatorStyle(palette, metrics),
    LocalScrollbarStyle provides ScrollbarStyle(),
    LocalIndication provides NoIndication,
    content = content
)
