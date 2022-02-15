package org.jetbrains.jewel.theme.intellij

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import org.jetbrains.jewel.NoIndication
import org.jetbrains.jewel.styles.LocalTextStyle
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.intellij.styles.ButtonStyle
import org.jetbrains.jewel.theme.intellij.styles.CheckboxStyle
import org.jetbrains.jewel.theme.intellij.styles.FrameStyle
import org.jetbrains.jewel.theme.intellij.styles.LocalButtonStyle
import org.jetbrains.jewel.theme.intellij.styles.LocalCheckboxStyle
import org.jetbrains.jewel.theme.intellij.styles.LocalFrameStyle
import org.jetbrains.jewel.theme.intellij.styles.LocalIconButtonStyle
import org.jetbrains.jewel.theme.intellij.styles.LocalSeparatorStyle
import org.jetbrains.jewel.theme.intellij.styles.LocalTextFieldStyle
import org.jetbrains.jewel.theme.intellij.styles.ScrollbarStyle
import org.jetbrains.jewel.theme.intellij.styles.SeparatorStyle
import org.jetbrains.jewel.theme.intellij.styles.TextFieldStyle

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
    LocalSeparatorStyle provides SeparatorStyle(palette, metrics),
    LocalScrollbarStyle provides ScrollbarStyle(palette, metrics),
    LocalIndication provides NoIndication,
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