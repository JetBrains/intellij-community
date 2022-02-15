package org.jetbrains.jewel.theme.toolbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided

@Immutable
data class ToolboxTypography(
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val smallBody: TextStyle,
    val control: TextStyle,
    val caption: TextStyle,
) {

    constructor(
        defaultFontFamily: FontFamily = FontFamily.Default,

        title: TextStyle = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            letterSpacing = 0.25.sp
        ),
        subtitle: TextStyle = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            letterSpacing = 0.15.sp
        ),
        body: TextStyle = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            letterSpacing = 0.5.sp
        ),
        smallBody: TextStyle = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            letterSpacing = 0.25.sp
        ),
        control: TextStyle = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            letterSpacing = 1.25.sp
        ),
        caption: TextStyle = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp
        ),
    ) : this(
        title = title.withDefaultFontFamily(defaultFontFamily),
        subtitle = subtitle.withDefaultFontFamily(defaultFontFamily),
        body = body.withDefaultFontFamily(defaultFontFamily),
        smallBody = smallBody.withDefaultFontFamily(defaultFontFamily),
        control = control.withDefaultFontFamily(defaultFontFamily),
        caption = caption.withDefaultFontFamily(defaultFontFamily),
    )
}

private fun TextStyle.withDefaultFontFamily(default: FontFamily): TextStyle {
    return if (fontFamily != null) this else copy(fontFamily = default)
}

val LocalTypography = staticCompositionLocalOf<ToolboxTypography> { localNotProvided() }
val Styles.typography: ToolboxTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalTypography.current

fun Typography(metrics: ToolboxMetrics, fontFamily: FontFamily = FontFamily.Default): ToolboxTypography {
    val baseSize = (metrics.base.value * 2).sp
    return ToolboxTypography(
        fontFamily,
        title = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = baseSize * 2,
            letterSpacing = 0.25.sp
        ),
        subtitle = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = baseSize * 1.5,
            letterSpacing = 0.15.sp
        ),
        body = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = baseSize,
            letterSpacing = 0.5.sp
        ),
        smallBody = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = baseSize * 0.8,
            letterSpacing = 0.25.sp
        ),
        control = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = baseSize,
            letterSpacing = 1.25.sp
        ),
        caption = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = baseSize * 0.75,
            letterSpacing = 0.4.sp
        ),
    )
}
