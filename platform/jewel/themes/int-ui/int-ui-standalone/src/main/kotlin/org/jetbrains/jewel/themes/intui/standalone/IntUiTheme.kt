package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.IntelliJTheme

abstract class IntUiTheme(
    protected val palette: IntUiColorPalette
) : IntelliJTheme {

    override fun providedCompositionLocalValues(): Array<ProvidedValue<*>> = arrayOf(
        LocalIntUiPalette provides palette
    )

    override val defaultTextStyle: TextStyle = TextStyle.Default.copy(
        fontFamily = FontFamily.Inter,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal
    )

    companion object {

        val palette: IntUiColorPalette
            @Composable
            @ReadOnlyComposable
            get() = LocalIntUiPalette.current
    }
}
