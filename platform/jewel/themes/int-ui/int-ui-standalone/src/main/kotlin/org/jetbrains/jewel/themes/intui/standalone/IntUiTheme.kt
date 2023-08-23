package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.GlobalMetrics
import org.jetbrains.jewel.IntelliJComponentStyling
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.SvgLoader
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
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.styling.TextFieldStyle
import org.jetbrains.jewel.themes.PaletteMapperFactory
import org.jetbrains.jewel.themes.intui.core.BaseIntUiTheme
import org.jetbrains.jewel.themes.intui.core.IntUiThemeColorPalette
import org.jetbrains.jewel.themes.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.themes.intui.core.IntelliJSvgLoader
import org.jetbrains.jewel.themes.intui.core.IntelliJSvgPatcher
import org.jetbrains.jewel.themes.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.themes.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme.defaultComponentStyling
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
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiTabStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiTextAreaStyle
import org.jetbrains.jewel.themes.intui.standalone.styling.IntUiTextFieldStyle

object IntUiTheme : BaseIntUiTheme {

    private val intUiDefaultTextStyle = TextStyle.Default.copy(
        fontFamily = FontFamily.Inter,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal
    )

    override val defaultLightTextStyle = intUiDefaultTextStyle.copy(color = IntUiLightTheme.colors.grey(1))
    override val defaultDarkTextStyle = intUiDefaultTextStyle.copy(color = IntUiDarkTheme.colors.grey(12))

    @Composable
    fun lightThemeDefinition(
        colors: GlobalColors = IntUiGlobalColors.light(),
        metrics: GlobalMetrics = IntUiGlobalMetrics(),
        palette: IntUiThemeColorPalette = IntUiLightTheme.colors,
        icons: IntelliJThemeIconData = IntUiLightTheme.icons,
        defaultTextStyle: TextStyle = defaultLightTextStyle,
    ) = IntUiThemeDefinition(isDark = false, colors, palette, icons, metrics, defaultTextStyle)

    @Composable
    fun darkThemeDefinition(
        colors: GlobalColors = IntUiGlobalColors.dark(),
        metrics: GlobalMetrics = IntUiGlobalMetrics(),
        palette: IntUiThemeColorPalette = IntUiDarkTheme.colors,
        icons: IntelliJThemeIconData = IntUiDarkTheme.icons,
        defaultTextStyle: TextStyle = defaultDarkTextStyle,
    ) = IntUiThemeDefinition(isDark = true, colors, palette, icons, metrics, defaultTextStyle)

    @Composable
    fun defaultComponentStyling(isDark: Boolean, svgLoader: SvgLoader) =
        if (isDark) darkComponentStyling(svgLoader) else lightComponentStyling(svgLoader)

    @Composable
    fun darkComponentStyling(
        svgLoader: SvgLoader,
        defaultButtonStyle: ButtonStyle = IntUiButtonStyle.Default.dark(),
        outlinedButtonStyle: ButtonStyle = IntUiButtonStyle.Outlined.dark(),
        checkboxStyle: CheckboxStyle = IntUiCheckboxStyle.dark(svgLoader),
        chipStyle: ChipStyle = IntUiChipStyle.dark(),
        dropdownStyle: DropdownStyle = IntUiDropdownStyle.dark(svgLoader),
        groupHeaderStyle: GroupHeaderStyle = IntUiGroupHeaderStyle.dark(),
        labelledTextFieldStyle: LabelledTextFieldStyle = IntUiLabelledTextFieldStyle.dark(),
        linkStyle: LinkStyle = IntUiLinkStyle.dark(svgLoader),
        menuStyle: MenuStyle = IntUiMenuStyle.dark(),
        horizontalProgressBarStyle: HorizontalProgressBarStyle = IntUiHorizontalProgressBarStyle.dark(),
        radioButtonStyle: RadioButtonStyle = IntUiRadioButtonStyle.dark(svgLoader),
        scrollbarStyle: ScrollbarStyle = IntUiScrollbarStyle.dark(),
        textAreaStyle: IntUiTextAreaStyle = IntUiTextAreaStyle.dark(),
        textFieldStyle: TextFieldStyle = IntUiTextFieldStyle.dark(),
        lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.dark(),
        defaultTabStyle: TabStyle = IntUiTabStyle.Default.dark(svgLoader),
        editorTabStyle: TabStyle = IntUiTabStyle.Editor.dark(svgLoader),
    ) =
        IntelliJComponentStyling(
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
            lazyTreeStyle = lazyTreeStyle,
            defaultTabStyle = defaultTabStyle,
            editorTabStyle = editorTabStyle
        )

    @Composable
    fun lightComponentStyling(
        svgLoader: SvgLoader,
        defaultButtonStyle: ButtonStyle = IntUiButtonStyle.Default.light(),
        outlinedButtonStyle: ButtonStyle = IntUiButtonStyle.Outlined.light(),
        checkboxStyle: CheckboxStyle = IntUiCheckboxStyle.light(svgLoader),
        chipStyle: ChipStyle = IntUiChipStyle.light(),
        dropdownStyle: DropdownStyle = IntUiDropdownStyle.light(svgLoader),
        groupHeaderStyle: GroupHeaderStyle = IntUiGroupHeaderStyle.light(),
        labelledTextFieldStyle: LabelledTextFieldStyle = IntUiLabelledTextFieldStyle.light(),
        linkStyle: LinkStyle = IntUiLinkStyle.light(svgLoader),
        menuStyle: MenuStyle = IntUiMenuStyle.light(),
        horizontalProgressBarStyle: HorizontalProgressBarStyle = IntUiHorizontalProgressBarStyle.light(),
        radioButtonStyle: RadioButtonStyle = IntUiRadioButtonStyle.light(svgLoader),
        scrollbarStyle: ScrollbarStyle = IntUiScrollbarStyle.light(),
        textAreaStyle: IntUiTextAreaStyle = IntUiTextAreaStyle.light(),
        textFieldStyle: TextFieldStyle = IntUiTextFieldStyle.light(),
        lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.light(),
        defaultTabStyle: TabStyle = IntUiTabStyle.Default.light(svgLoader),
        editorTabStyle: TabStyle = IntUiTabStyle.Editor.light(svgLoader),
    ) =
        IntelliJComponentStyling(
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
            lazyTreeStyle = lazyTreeStyle,
            defaultTabStyle = defaultTabStyle,
            editorTabStyle = editorTabStyle
        )
}

@OptIn(ExperimentalJewelApi::class)
@Composable
fun IntUiTheme(theme: IntUiThemeDefinition, swingCompatMode: Boolean = false, content: @Composable () -> Unit) {
    val svgLoader by remember(theme.isDark, theme.iconData, theme.colorPalette) {
        val paletteMapper = PaletteMapperFactory.create(theme.isDark, theme.iconData, theme.colorPalette)
        val svgPatcher = IntelliJSvgPatcher(paletteMapper)
        mutableStateOf(IntelliJSvgLoader(svgPatcher))
    }

    val componentStyling = defaultComponentStyling(theme.isDark, svgLoader)
    IntUiTheme(theme, componentStyling, swingCompatMode, content)
}

@ExperimentalJewelApi
@Composable
fun IntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: IntelliJComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    BaseIntUiTheme(theme, componentStyling, swingCompatMode) {
        CompositionLocalProvider(LocalResourceLoader provides IntUiDefaultResourceLoader) {
            content()
        }
    }
}
