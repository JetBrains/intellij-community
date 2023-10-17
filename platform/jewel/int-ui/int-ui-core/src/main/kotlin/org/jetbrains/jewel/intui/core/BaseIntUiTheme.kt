package org.jetbrains.jewel.intui.core

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.GlobalMetrics
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.LocalColorPalette
import org.jetbrains.jewel.LocalIconData
import org.jetbrains.jewel.NoIndication
import org.jetbrains.jewel.styling.ButtonStyle
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.ChipStyle
import org.jetbrains.jewel.styling.CircularProgressStyle
import org.jetbrains.jewel.styling.DividerStyle
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.GroupHeaderStyle
import org.jetbrains.jewel.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.styling.LabelledTextFieldStyle
import org.jetbrains.jewel.styling.LazyTreeStyle
import org.jetbrains.jewel.styling.LinkStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ScrollbarStyle
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.styling.TextAreaStyle
import org.jetbrains.jewel.styling.TextFieldStyle

interface BaseIntUiTheme : IntelliJTheme {

    val globalColors: GlobalColors
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.globalColors

    val globalMetrics: GlobalMetrics
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.globalMetrics

    val textStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.textStyle

    val contentColor: Color
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.contentColor

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

    val dividerStyle: DividerStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.dividerStyle

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

    val circularProgressStyle: CircularProgressStyle
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.circularProgressStyle
}

@Composable
fun BaseIntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: @Composable () -> Array<ProvidedValue<*>>,
    content: @Composable () -> Unit,
) {
    BaseIntUiTheme(theme, componentStyling, swingCompatMode = false, content)
}

@Composable
fun BaseIntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: @Composable () -> Array<ProvidedValue<*>>,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    IntelliJTheme(theme, swingCompatMode) {
        CompositionLocalProvider(
            LocalColorPalette provides theme.colorPalette,
            LocalIconData provides theme.iconData,
            LocalIndication provides NoIndication,
        ) {
            CompositionLocalProvider(values = componentStyling(), content = content)
        }
    }
}
