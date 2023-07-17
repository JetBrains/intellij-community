package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.ThemeColors
import org.jetbrains.jewel.ThemeMetrics
import org.jetbrains.jewel.styling.ButtonStyle
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.ChipStyle
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.GroupHeaderStyle
import org.jetbrains.jewel.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.styling.LabelledTextFieldStyle
import org.jetbrains.jewel.styling.LazyTreeStyle
import org.jetbrains.jewel.styling.LinkStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ScrollbarStyle
import org.jetbrains.jewel.styling.TextAreaStyle
import org.jetbrains.jewel.styling.TextFieldStyle
import org.jetbrains.jewel.themes.intui.core.BaseIntUiTheme
import org.jetbrains.jewel.themes.intui.core.ComponentStyling
import org.jetbrains.jewel.themes.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.themes.intui.core.IntelliJThemeColorPalette
import org.jetbrains.jewel.themes.intui.core.IntelliJThemeIcons
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiButtonStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiCheckboxStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiChipStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiDropdownStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiGroupHeaderStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiHorizontalProgressBarStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiLabelledTextFieldStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiLazyTreeStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiLinkStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiMenuStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiRadioButtonStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiScrollbarStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiTextAreaStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiTextFieldStyle

object IntUiTheme : BaseIntUiTheme {

    private val intUiDefaultTextStyle = TextStyle.Default.copy(
        fontFamily = FontFamily.Inter,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal
    )

    @Composable
    fun light(
        colors: ThemeColors = IntUiThemeColors.light(),
        metrics: ThemeMetrics = IntUiThemeMetrics(),
        palette: IntelliJThemeColorPalette = IntUiLightTheme.colors,
        icons: IntelliJThemeIcons = IntUiLightTheme.icons,
        defaultTextStyle: TextStyle = intUiDefaultTextStyle
    ) = IntUiThemeDefinition(isDark = false, colors, palette, icons, metrics, defaultTextStyle)

    @Composable
    fun dark(
        colors: ThemeColors = IntUiThemeColors.dark(),
        metrics: ThemeMetrics = IntUiThemeMetrics(),
        palette: IntelliJThemeColorPalette = IntUiDarkTheme.colors,
        icons: IntelliJThemeIcons = IntUiDarkTheme.icons,
        defaultTextStyle: TextStyle = intUiDefaultTextStyle
    ) = IntUiThemeDefinition(isDark = true, colors, palette, icons, metrics, defaultTextStyle)

    val colors: ThemeColors
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.colors

    val metrics: ThemeMetrics
        @Composable
        @ReadOnlyComposable
        get() = IntelliJTheme.metrics

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
}

@OptIn(ExperimentalJewelApi::class)
@Composable
fun IntUiTheme(theme: IntUiThemeDefinition, swingCompatMode: Boolean = false, content: @Composable () -> Unit) {
    val componentStyling = defaultComponentStyling(theme.isDark)
    IntUiTheme(theme, componentStyling, swingCompatMode, content)
}

@ExperimentalJewelApi
@Composable
fun IntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: ComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit
) {
    BaseIntUiTheme(theme, componentStyling, swingCompatMode) {
        CompositionLocalProvider(LocalResourceLoader provides IntUiDefaultResourceLoader) {
            content()
        }
    }
}

@Composable
fun defaultComponentStyling(isDark: Boolean) =
    if (isDark) darkComponentStyling() else lightComponentStyling()

@Composable
fun darkComponentStyling(
    defaultButtonStyle: ButtonStyle = IntUiButtonStyle.Default.dark(),
    outlinedButtonStyle: ButtonStyle = IntUiButtonStyle.Outlined.dark(),
    checkboxStyle: CheckboxStyle = IntUiCheckboxStyle.dark(),
    chipStyle: ChipStyle = IntUiChipStyle.dark(),
    dropdownStyle: DropdownStyle = IntUiDropdownStyle.dark(),
    groupHeaderStyle: GroupHeaderStyle = IntUiGroupHeaderStyle.dark(),
    labelledTextFieldStyle: LabelledTextFieldStyle = IntUiLabelledTextFieldStyle.dark(),
    linkStyle: LinkStyle = IntUiLinkStyle.dark(),
    menuStyle: MenuStyle = IntUiMenuStyle.dark(),
    horizontalProgressBarStyle: HorizontalProgressBarStyle = IntUiHorizontalProgressBarStyle.dark(),
    radioButtonStyle: RadioButtonStyle = IntUiRadioButtonStyle.dark(),
    scrollbarStyle: ScrollbarStyle = IntUiScrollbarStyle.dark(),
    textAreaStyle: IntUiTextAreaStyle = IntUiTextAreaStyle.dark(),
    textFieldStyle: TextFieldStyle = IntUiTextFieldStyle.dark(),
    lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.dark()
) =
    ComponentStyling(
        defaultButtonStyle = defaultButtonStyle,
        outlinedButtonStyle = outlinedButtonStyle,
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        dropdownStyle = dropdownStyle,
        groupHeaderStyle = groupHeaderStyle,
        labelledTextFieldStyle = labelledTextFieldStyle,
        linkStyle = linkStyle,
        menuStyle = menuStyle,
        horizontalProgressBarStyle = horizontalProgressBarStyle,
        radioButtonStyle = radioButtonStyle,
        scrollbarStyle = scrollbarStyle,
        textAreaStyle = textAreaStyle,
        textFieldStyle = textFieldStyle,
        lazyTreeStyle = lazyTreeStyle
    )

@Composable
fun lightComponentStyling(
    defaultButtonStyle: ButtonStyle = IntUiButtonStyle.Default.light(),
    outlinedButtonStyle: ButtonStyle = IntUiButtonStyle.Outlined.light(),
    checkboxStyle: CheckboxStyle = IntUiCheckboxStyle.light(),
    chipStyle: ChipStyle = IntUiChipStyle.light(),
    dropdownStyle: DropdownStyle = IntUiDropdownStyle.light(),
    groupHeaderStyle: GroupHeaderStyle = IntUiGroupHeaderStyle.light(),
    labelledTextFieldStyle: LabelledTextFieldStyle = IntUiLabelledTextFieldStyle.light(),
    linkStyle: LinkStyle = IntUiLinkStyle.light(),
    menuStyle: MenuStyle = IntUiMenuStyle.light(),
    horizontalProgressBarStyle: HorizontalProgressBarStyle = IntUiHorizontalProgressBarStyle.light(),
    radioButtonStyle: RadioButtonStyle = IntUiRadioButtonStyle.light(),
    scrollbarStyle: ScrollbarStyle = IntUiScrollbarStyle.light(),
    textAreaStyle: IntUiTextAreaStyle = IntUiTextAreaStyle.light(),
    textFieldStyle: TextFieldStyle = IntUiTextFieldStyle.light(),
    lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.light()
) =
    ComponentStyling(
        defaultButtonStyle = defaultButtonStyle,
        outlinedButtonStyle = outlinedButtonStyle,
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        dropdownStyle = dropdownStyle,
        groupHeaderStyle = groupHeaderStyle,
        labelledTextFieldStyle = labelledTextFieldStyle,
        linkStyle = linkStyle,
        menuStyle = menuStyle,
        horizontalProgressBarStyle = horizontalProgressBarStyle,
        radioButtonStyle = radioButtonStyle,
        scrollbarStyle = scrollbarStyle,
        textAreaStyle = textAreaStyle,
        textFieldStyle = textFieldStyle,
        lazyTreeStyle = lazyTreeStyle
    )
