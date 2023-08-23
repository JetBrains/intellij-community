package org.jetbrains.jewel.themes.intui.core

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.GlobalMetrics
import org.jetbrains.jewel.IntelliJComponentStyling
import org.jetbrains.jewel.IntelliJContextMenuRepresentation
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.LocalColorPalette
import org.jetbrains.jewel.LocalIconData
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
import org.jetbrains.jewel.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.styling.LocalDropdownStyle
import org.jetbrains.jewel.styling.LocalEditorTabStyle
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
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.styling.TextAreaStyle
import org.jetbrains.jewel.styling.TextFieldStyle

interface BaseIntUiTheme : IntelliJTheme {

    val defaultLightTextStyle: TextStyle
    val defaultDarkTextStyle: TextStyle

    val globalColors: GlobalColors
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.globalColors

    val globalMetrics: GlobalMetrics
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.globalMetrics

    val defaultTextStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.defaultTextStyle

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.isDark

    val isSwingCompatMode: Boolean
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.isSwingCompatMode

    val iconData: IntelliJThemeIconData
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.iconData

    val colorPalette: IntUiThemeColorPalette
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.colorPalette as? IntUiThemeColorPalette ?: EmptyIntUiThemeColorPalette

    val defaultButtonStyle: ButtonStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.defaultButtonStyle

    val outlinedButtonStyle: ButtonStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.outlinedButtonStyle

    val checkboxStyle: CheckboxStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.checkboxStyle

    val chipStyle: ChipStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.chipStyle

    val dropdownStyle: DropdownStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.dropdownStyle

    val groupHeaderStyle: GroupHeaderStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.groupHeaderStyle

    val labelledTextFieldStyle: LabelledTextFieldStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.labelledTextFieldStyle

    val linkStyle: LinkStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.linkStyle

    val menuStyle: MenuStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.menuStyle

    val horizontalProgressBarStyle: HorizontalProgressBarStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.horizontalProgressBarStyle

    val radioButtonStyle: RadioButtonStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.radioButtonStyle

    val scrollbarStyle: ScrollbarStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.scrollbarStyle

    val textAreaStyle: TextAreaStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.textAreaStyle

    val textFieldStyle: TextFieldStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.textFieldStyle

    val treeStyle: LazyTreeStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.treeStyle

    val defaultTabStyle: TabStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.defaultTabStyle

    val editorTabStyle: TabStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.editorTabStyle
}

@OptIn(ExperimentalJewelApi::class)
@Composable
fun BaseIntUiTheme(theme: IntUiThemeDefinition, componentStyling: IntelliJComponentStyling, content: @Composable () -> Unit) {
    BaseIntUiTheme(theme, componentStyling, swingCompatMode = false, content)
}

@ExperimentalJewelApi
@Composable
fun BaseIntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: IntelliJComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalColorPalette provides theme.colorPalette,
        LocalIconData provides theme.iconData,
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
        LocalTextFieldStyle provides componentStyling.textFieldStyle,
        LocalDefaultTabStyle provides componentStyling.defaultTabStyle,
        LocalEditorTabStyle provides componentStyling.editorTabStyle
    ) {
        IntelliJTheme(theme, swingCompatMode, content)
    }
}
