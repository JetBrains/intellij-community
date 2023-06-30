package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.styling.ButtonStyle
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.ChipStyle
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.GroupHeaderStyle
import org.jetbrains.jewel.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.styling.LabelledTextFieldStyle
import org.jetbrains.jewel.styling.LazyTreeStyle
import org.jetbrains.jewel.styling.LinkStyle
import org.jetbrains.jewel.styling.LocalCheckboxStyle
import org.jetbrains.jewel.styling.LocalChipStyle
import org.jetbrains.jewel.styling.LocalDefaultButtonStyle
import org.jetbrains.jewel.styling.LocalDropdownStyle
import org.jetbrains.jewel.styling.LocalGroupHeaderStyle
import org.jetbrains.jewel.styling.LocalHorizontalProgressBarStyle
import org.jetbrains.jewel.styling.LocalLabelledTextFieldStyle
import org.jetbrains.jewel.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.styling.LocalLinkStyle
import org.jetbrains.jewel.styling.LocalMenuStyle
import org.jetbrains.jewel.styling.LocalOutlinedButtonStyle
import org.jetbrains.jewel.styling.LocalRadioButtonStyle
import org.jetbrains.jewel.styling.LocalScrollbarStyle
import org.jetbrains.jewel.styling.LocalTextAreaStyle
import org.jetbrains.jewel.styling.LocalTextFieldStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ScrollbarStyle
import org.jetbrains.jewel.styling.TextAreaStyle
import org.jetbrains.jewel.styling.TextFieldStyle

interface IntelliJTheme {

    companion object {

        // -------------
        // Global values
        // -------------

        val colors: ThemeColors
            @Composable
            @ReadOnlyComposable
            get() = LocalThemeColors.current

        val metrics: ThemeMetrics
            @Composable
            @ReadOnlyComposable
            get() = LocalThemeMetrics.current

        val defaultTextStyle: TextStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalTextStyle.current

        val isDark: Boolean
            @Composable
            @ReadOnlyComposable
            get() = LocalIsDarkTheme.current

        val isSwingCompatMode
            @Composable
            @ReadOnlyComposable
            get() = LocalSwingCompatMode.current

        // -----------------
        // Component styling
        // -----------------

        val defaultButtonStyle: ButtonStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalDefaultButtonStyle.current

        val outlinedButtonStyle: ButtonStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalOutlinedButtonStyle.current

        val checkboxStyle: CheckboxStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalCheckboxStyle.current

        val chipStyle: ChipStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalChipStyle.current

        val dropdownStyle: DropdownStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalDropdownStyle.current

        val groupHeaderStyle: GroupHeaderStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalGroupHeaderStyle.current

        val labelledTextFieldStyle: LabelledTextFieldStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalLabelledTextFieldStyle.current

        val linkStyle: LinkStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalLinkStyle.current

        val menuStyle: MenuStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalMenuStyle.current

        val horizontalProgressBarStyle: HorizontalProgressBarStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalHorizontalProgressBarStyle.current

        val radioButtonStyle: RadioButtonStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalRadioButtonStyle.current

        val scrollbarStyle: ScrollbarStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalScrollbarStyle.current

        val textAreaStyle: TextAreaStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalTextAreaStyle.current

        val textFieldStyle: TextFieldStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalTextFieldStyle.current

        val treeStyle: LazyTreeStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalLazyTreeStyle.current
    }
}

@ExperimentalJewelApi
@Composable
fun IntelliJTheme(
    theme: IntelliJThemeDefinition,
    swingCompatMode: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalSwingCompatMode provides swingCompatMode) {
        IntelliJTheme(theme, content)
    }
}

@Composable
fun IntelliJTheme(theme: IntelliJThemeDefinition, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides theme.defaultTextStyle.color,
        LocalIsDarkTheme provides theme.isDark,
        LocalTextStyle provides theme.defaultTextStyle,
        LocalThemeColors provides theme.colors,
        LocalThemeMetrics provides theme.metrics,
        content = content
    )
}

internal val LocalIsDarkTheme = staticCompositionLocalOf<Boolean> {
    error("No InDarkTheme provided")
}

internal val LocalSwingCompatMode = staticCompositionLocalOf {
    // By default, Swing compat is not enabled
    false
}
