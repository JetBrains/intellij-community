package org.jetbrains.jewel

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import org.jetbrains.jewel.styles.ButtonStyle
import org.jetbrains.jewel.styles.CheckboxStyle
import org.jetbrains.jewel.styles.FrameStyle
import org.jetbrains.jewel.styles.LocalButtonStyle
import org.jetbrains.jewel.styles.LocalCheckboxStyle
import org.jetbrains.jewel.styles.LocalFrameStyle
import org.jetbrains.jewel.styles.LocalIconButtonStyle
import org.jetbrains.jewel.styles.LocalRadioButtonStyle
import org.jetbrains.jewel.styles.LocalSeparatorStyle
import org.jetbrains.jewel.styles.LocalSliderStyle
import org.jetbrains.jewel.styles.LocalTabStyle
import org.jetbrains.jewel.styles.LocalTextFieldStyle
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.LocalTreeViewStyle
import org.jetbrains.jewel.styles.RadioButtonStyle
import org.jetbrains.jewel.styles.ScrollbarStyle
import org.jetbrains.jewel.styles.SeparatorStyle
import org.jetbrains.jewel.styles.SliderStyle
import org.jetbrains.jewel.styles.TabStyle
import org.jetbrains.jewel.styles.TextFieldStyle
import org.jetbrains.jewel.styles.TreeViewStyle
import org.jetbrains.jewel.styles.localNotProvided

val LocalTypography = compositionLocalOf<IntelliJTypography> { localNotProvided() }
val LocalMetrics = compositionLocalOf<IntelliJMetrics> { localNotProvided() }
val LocalPainters = compositionLocalOf<IntelliJPainters> { localNotProvided() }
val LocalPalette = compositionLocalOf<IntelliJPalette> { localNotProvided() }

@Composable
fun IntelliJTheme(
    palette: IntelliJPalette,
    metrics: IntelliJMetrics,
    painters: IntelliJPainters,
    typography: IntelliJTypography,
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalFrameStyle provides FrameStyle(palette),
    LocalTextStyle provides typography.default,
    LocalButtonStyle provides ButtonStyle(palette, metrics, typography.button),
    LocalIconButtonStyle provides ButtonStyle(palette, metrics, typography.button),
    LocalCheckboxStyle provides CheckboxStyle(palette, painters, typography.checkBox),
    LocalTextFieldStyle provides TextFieldStyle(palette, metrics, typography.textField),
    LocalRadioButtonStyle provides RadioButtonStyle(palette, painters, typography.radioButton),
    LocalSeparatorStyle provides SeparatorStyle(palette, metrics),
    LocalScrollbarStyle provides ScrollbarStyle(palette, metrics),
    LocalTreeViewStyle provides TreeViewStyle(palette, metrics, painters),
    LocalSliderStyle provides SliderStyle(palette, typography),
    LocalIndication provides NoIndication,
    LocalTabStyle provides TabStyle(palette, typography.default),
    LocalTypography provides typography,
    LocalMetrics provides metrics,
    LocalPainters provides painters,
    LocalPalette provides palette,
    content = content
)

object IntelliJTheme {

    val typography
        @Composable
        get() = LocalTypography.current

    val metrics
        @Composable
        get() = LocalMetrics.current

    val painters
        @Composable
        get() = LocalPainters.current

    val palette
        @Composable
        get() = LocalPalette.current
}
