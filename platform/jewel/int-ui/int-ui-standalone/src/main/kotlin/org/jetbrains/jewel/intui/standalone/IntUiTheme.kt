package org.jetbrains.jewel.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.GlobalColors
import org.jetbrains.jewel.GlobalMetrics
import org.jetbrains.jewel.IntelliJComponentStyling
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.JewelSvgLoader
import org.jetbrains.jewel.LocalColorPalette
import org.jetbrains.jewel.LocalContentColor
import org.jetbrains.jewel.LocalGlobalColors
import org.jetbrains.jewel.LocalGlobalMetrics
import org.jetbrains.jewel.LocalIconData
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.LocalTextStyle
import org.jetbrains.jewel.SimpleResourceLoader
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.intui.core.BaseIntUiTheme
import org.jetbrains.jewel.intui.core.IntUiThemeColorPalette
import org.jetbrains.jewel.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.intui.core.IntelliJSvgPatcher
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.IntUiTheme.defaultComponentStyling
import org.jetbrains.jewel.intui.standalone.styling.IntUiButtonStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiCheckboxStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiChipStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiCircularProgressStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiGroupHeaderStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiHorizontalProgressBarStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiLabelledTextFieldStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiLazyTreeStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiLinkStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiRadioButtonStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiScrollbarStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiTabStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextAreaStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextFieldStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiTooltipStyle
import org.jetbrains.jewel.styling.ButtonStyle
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.ChipStyle
import org.jetbrains.jewel.styling.CircularProgressStyle
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
import org.jetbrains.jewel.themes.StandalonePaletteMapperFactory

object IntUiTheme : BaseIntUiTheme {

    val defaultTextStyle = TextStyle.Default.copy(
        fontFamily = FontFamily.Inter,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal,
    )

    @Composable
    fun lightThemeDefinition(
        colors: GlobalColors = IntUiGlobalColors.light(),
        metrics: GlobalMetrics = IntUiGlobalMetrics(),
        palette: IntUiThemeColorPalette = IntUiLightTheme.colors,
        icons: IntelliJThemeIconData = IntUiLightTheme.icons,
        defaultTextStyle: TextStyle = this.defaultTextStyle,
        contentColor: Color = IntUiLightTheme.colors.grey(1),
    ) = IntUiThemeDefinition(isDark = false, colors, palette, icons, metrics, defaultTextStyle, contentColor)

    @Composable
    fun darkThemeDefinition(
        colors: GlobalColors = IntUiGlobalColors.dark(),
        metrics: GlobalMetrics = IntUiGlobalMetrics(),
        palette: IntUiThemeColorPalette = IntUiDarkTheme.colors,
        icons: IntelliJThemeIconData = IntUiDarkTheme.icons,
        defaultTextStyle: TextStyle = this.defaultTextStyle,
        contentColor: Color = IntUiDarkTheme.colors.grey(12),
    ) = IntUiThemeDefinition(isDark = true, colors, palette, icons, metrics, defaultTextStyle, contentColor)

    @Composable
    fun defaultComponentStyling(theme: IntUiThemeDefinition, svgLoader: SvgLoader): IntelliJComponentStyling {
        lateinit var styling: IntelliJComponentStyling
        CompositionLocalProvider(
            LocalColorPalette provides theme.colorPalette,
            LocalTextStyle provides theme.defaultTextStyle,
            LocalContentColor provides theme.defaultTextStyle.color,
            LocalIconData provides theme.iconData,
            LocalGlobalColors provides theme.globalColors,
            LocalGlobalMetrics provides theme.globalMetrics,
        ) {
            styling = if (theme.isDark) darkComponentStyling(svgLoader) else lightComponentStyling(svgLoader)
        }
        return styling
    }

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
        menuStyle: MenuStyle = IntUiMenuStyle.dark(svgLoader),
        horizontalProgressBarStyle: HorizontalProgressBarStyle = IntUiHorizontalProgressBarStyle.dark(),
        radioButtonStyle: RadioButtonStyle = IntUiRadioButtonStyle.dark(svgLoader),
        scrollbarStyle: ScrollbarStyle = IntUiScrollbarStyle.dark(),
        textAreaStyle: IntUiTextAreaStyle = IntUiTextAreaStyle.dark(),
        textFieldStyle: TextFieldStyle = IntUiTextFieldStyle.dark(),
        lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.dark(svgLoader),
        defaultTabStyle: TabStyle = IntUiTabStyle.Default.dark(svgLoader),
        editorTabStyle: TabStyle = IntUiTabStyle.Editor.dark(svgLoader),
        circularProgressStyle: CircularProgressStyle = IntUiCircularProgressStyle.dark(),
        tooltipStyle: IntUiTooltipStyle = IntUiTooltipStyle.dark(),
    ) =
        IntelliJComponentStyling(
            checkboxStyle = checkboxStyle,
            chipStyle = chipStyle,
            defaultButtonStyle = defaultButtonStyle,
            defaultTabStyle = defaultTabStyle,
            dropdownStyle = dropdownStyle,
            editorTabStyle = editorTabStyle,
            groupHeaderStyle = groupHeaderStyle,
            horizontalProgressBarStyle = horizontalProgressBarStyle,
            labelledTextFieldStyle = labelledTextFieldStyle,
            lazyTreeStyle = lazyTreeStyle,
            linkStyle = linkStyle,
            menuStyle = menuStyle,
            outlinedButtonStyle = outlinedButtonStyle,
            radioButtonStyle = radioButtonStyle,
            scrollbarStyle = scrollbarStyle,
            textAreaStyle = textAreaStyle,
            textFieldStyle = textFieldStyle,
            circularProgressStyle = circularProgressStyle,
            tooltipStyle = tooltipStyle,
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
        menuStyle: MenuStyle = IntUiMenuStyle.light(svgLoader),
        horizontalProgressBarStyle: HorizontalProgressBarStyle = IntUiHorizontalProgressBarStyle.light(),
        radioButtonStyle: RadioButtonStyle = IntUiRadioButtonStyle.light(svgLoader),
        scrollbarStyle: ScrollbarStyle = IntUiScrollbarStyle.light(),
        textAreaStyle: IntUiTextAreaStyle = IntUiTextAreaStyle.light(),
        textFieldStyle: TextFieldStyle = IntUiTextFieldStyle.light(),
        lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.light(svgLoader),
        defaultTabStyle: TabStyle = IntUiTabStyle.Default.light(svgLoader),
        editorTabStyle: TabStyle = IntUiTabStyle.Editor.light(svgLoader),
        circularProgressStyle: CircularProgressStyle = IntUiCircularProgressStyle.light(),
        tooltipStyle: IntUiTooltipStyle = IntUiTooltipStyle.light(),
    ) = IntelliJComponentStyling(
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        defaultButtonStyle = defaultButtonStyle,
        defaultTabStyle = defaultTabStyle,
        dropdownStyle = dropdownStyle,
        editorTabStyle = editorTabStyle,
        groupHeaderStyle = groupHeaderStyle,
        horizontalProgressBarStyle = horizontalProgressBarStyle,
        labelledTextFieldStyle = labelledTextFieldStyle,
        lazyTreeStyle = lazyTreeStyle,
        linkStyle = linkStyle,
        menuStyle = menuStyle,
        outlinedButtonStyle = outlinedButtonStyle,
        radioButtonStyle = radioButtonStyle,
        scrollbarStyle = scrollbarStyle,
        textAreaStyle = textAreaStyle,
        textFieldStyle = textFieldStyle,
        circularProgressStyle = circularProgressStyle,
        tooltipStyle = tooltipStyle,
    )
}

@Composable
fun IntUiTheme(
    themeDefinition: IntUiThemeDefinition,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val svgLoader by rememberSvgLoader(
        isDark = themeDefinition.isDark,
        iconData = themeDefinition.iconData,
        colorPalette = themeDefinition.colorPalette,
    )

    val componentStyling = defaultComponentStyling(themeDefinition, svgLoader)
    IntUiTheme(themeDefinition, componentStyling, swingCompatMode, content)
}

/**
 * Create and remember an instance of [SvgLoader].
 *
 * Note that since [SvgLoader] may cache the loaded images, and that
 * creating it may be somewhat expensive, you should only create it once at
 * the top level, and pass it around.
 */
@Composable
fun rememberSvgLoader(
    isDark: Boolean = IntUiTheme.isDark,
    iconData: IntelliJThemeIconData = IntUiTheme.iconData,
    colorPalette: IntUiThemeColorPalette = IntUiTheme.colorPalette,
) =
    remember(isDark, iconData, colorPalette) {
        val paletteMapper =
            StandalonePaletteMapperFactory.create(
                isDark,
                iconData,
                colorPalette,
            )
        val svgPatcher = IntelliJSvgPatcher(paletteMapper)
        mutableStateOf(JewelSvgLoader(svgPatcher))
    }

@Composable
fun IntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: IntelliJComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    BaseIntUiTheme(theme, componentStyling, swingCompatMode) {
        CompositionLocalProvider(LocalResourceLoader provides SimpleResourceLoader(IntUiTheme.javaClass.classLoader)) {
            content()
        }
    }
}
