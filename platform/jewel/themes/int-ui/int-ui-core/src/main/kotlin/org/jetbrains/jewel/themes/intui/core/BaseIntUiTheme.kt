package org.jetbrains.jewel.themes.intui.core

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.IntelliJContextMenuRepresentation
import org.jetbrains.jewel.IntelliJTheme
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

interface BaseIntUiTheme : IntelliJTheme {

    val palette: IntelliJThemeColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalIntUiPalette.current

    val icons: IntelliJThemeIcons
        @Composable
        @ReadOnlyComposable
        get() = LocalIntUiIcons.current
}

@OptIn(ExperimentalJewelApi::class)
@Composable
fun BaseIntUiTheme(theme: IntUiThemeDefinition, componentStyling: ComponentStyling, content: @Composable () -> Unit) {
    BaseIntUiTheme(theme, componentStyling, swingCompatMode = false, content)
}

@ExperimentalJewelApi
@Composable
fun BaseIntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: ComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalIntUiPalette provides theme.palette,
        LocalIntUiIcons provides theme.icons,
        LocalCheckboxStyle provides componentStyling.checkboxStyle,
        LocalChipStyle provides componentStyling.chipStyle,
        LocalContextMenuRepresentation provides IntelliJContextMenuRepresentation,
        LocalDefaultButtonStyle provides componentStyling.defaultButtonStyle,
        LocalDropdownStyle provides componentStyling.dropdownStyle,
        LocalGroupHeaderStyle provides componentStyling.groupHeaderStyle,
        LocalHorizontalProgressBarStyle provides componentStyling.horizontalProgressBarStyle,
        LocalLabelledTextFieldStyle provides componentStyling.labelledTextFieldStyle,
        LocalLazyTreeStyle provides componentStyling.lazyTreeStyle,
        LocalLinkStyle provides componentStyling.linkStyle,
        LocalMenuStyle provides componentStyling.menuStyle,
        LocalOutlinedButtonStyle provides componentStyling.outlinedButtonStyle,
        LocalRadioButtonStyle provides componentStyling.radioButtonStyle,
        LocalScrollbarStyle provides componentStyling.scrollbarStyle,
        LocalTextAreaStyle provides componentStyling.textAreaStyle,
        LocalTextFieldStyle provides componentStyling.textFieldStyle
    ) {
        IntelliJTheme(theme, swingCompatMode, content)
    }
}

data class ComponentStyling(
    val defaultButtonStyle: ButtonStyle,
    val outlinedButtonStyle: ButtonStyle,
    val checkboxStyle: CheckboxStyle,
    val chipStyle: ChipStyle,
    val dropdownStyle: DropdownStyle,
    val groupHeaderStyle: GroupHeaderStyle,
    val labelledTextFieldStyle: LabelledTextFieldStyle,
    val linkStyle: LinkStyle,
    val menuStyle: MenuStyle,
    val horizontalProgressBarStyle: HorizontalProgressBarStyle,
    val radioButtonStyle: RadioButtonStyle,
    val scrollbarStyle: ScrollbarStyle,
    val textAreaStyle: TextAreaStyle,
    val textFieldStyle: TextFieldStyle,
    val lazyTreeStyle: LazyTreeStyle
)
