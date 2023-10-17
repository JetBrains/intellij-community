package org.jetbrains.jewel.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
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
import org.jetbrains.jewel.intui.core.BaseIntUiTheme
import org.jetbrains.jewel.intui.core.IntUiThemeColorPalette
import org.jetbrains.jewel.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.IntUiTheme.defaultComponentStyling
import org.jetbrains.jewel.intui.standalone.styling.IntUiButtonStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiCheckboxStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiChipStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiCircularProgressStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiDividerStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiGroupHeaderStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiHorizontalProgressBarStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiIconButtonStyle
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
import org.jetbrains.jewel.painter.LocalPainterHintsProvider
import org.jetbrains.jewel.styling.ButtonStyle
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.ChipStyle
import org.jetbrains.jewel.styling.CircularProgressStyle
import org.jetbrains.jewel.styling.DividerStyle
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.GroupHeaderStyle
import org.jetbrains.jewel.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.styling.IconButtonStyle
import org.jetbrains.jewel.styling.LabelledTextFieldStyle
import org.jetbrains.jewel.styling.LazyTreeStyle
import org.jetbrains.jewel.styling.LinkStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ScrollbarStyle
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.styling.TextFieldStyle

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

    @Composable fun defaultComponentStyling(theme: IntUiThemeDefinition): IntelliJComponentStyling =
        if (theme.isDark) darkComponentStyling() else lightComponentStyling()

    @Composable fun darkComponentStyling(
        defaultButtonStyle: ButtonStyle = IntUiButtonStyle.Default.dark(),
        outlinedButtonStyle: ButtonStyle = IntUiButtonStyle.Outlined.dark(),
        checkboxStyle: CheckboxStyle = IntUiCheckboxStyle.dark(),
        chipStyle: ChipStyle = IntUiChipStyle.dark(),
        dividerStyle: DividerStyle = IntUiDividerStyle.dark(),
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
        lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.dark(),
        defaultTabStyle: TabStyle = IntUiTabStyle.Default.dark(),
        editorTabStyle: TabStyle = IntUiTabStyle.Editor.dark(),
        circularProgressStyle: CircularProgressStyle = IntUiCircularProgressStyle.dark(),
        tooltipStyle: IntUiTooltipStyle = IntUiTooltipStyle.dark(),
        iconButtonStyle: IconButtonStyle = IntUiIconButtonStyle.dark(),
    ) = IntelliJComponentStyling(
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        defaultButtonStyle = defaultButtonStyle,
        defaultTabStyle = defaultTabStyle,
        dividerStyle = dividerStyle,
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
        iconButtonStyle = iconButtonStyle,
    )

    @Composable fun lightComponentStyling(
        defaultButtonStyle: ButtonStyle = IntUiButtonStyle.Default.light(),
        outlinedButtonStyle: ButtonStyle = IntUiButtonStyle.Outlined.light(),
        checkboxStyle: CheckboxStyle = IntUiCheckboxStyle.light(),
        chipStyle: ChipStyle = IntUiChipStyle.light(),
        dividerStyle: DividerStyle = IntUiDividerStyle.light(),
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
        lazyTreeStyle: LazyTreeStyle = IntUiLazyTreeStyle.light(),
        defaultTabStyle: TabStyle = IntUiTabStyle.Default.light(),
        editorTabStyle: TabStyle = IntUiTabStyle.Editor.light(),
        circularProgressStyle: CircularProgressStyle = IntUiCircularProgressStyle.light(),
        tooltipStyle: IntUiTooltipStyle = IntUiTooltipStyle.light(),
        iconButtonStyle: IconButtonStyle = IntUiIconButtonStyle.light(),
    ) = IntelliJComponentStyling(
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        defaultButtonStyle = defaultButtonStyle,
        defaultTabStyle = defaultTabStyle,
        dividerStyle = dividerStyle,
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
        iconButtonStyle = iconButtonStyle,
    )
}

@Composable fun IntUiTheme(
    theme: IntUiThemeDefinition,
    componentStyling: @Composable () -> Array<ProvidedValue<*>>,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    BaseIntUiTheme(theme, {
        defaultComponentStyling(theme).providedStyles() + componentStyling()
    }, swingCompatMode) {
        CompositionLocalProvider(
            LocalPainterHintsProvider provides StandalonePainterHintsProvider(theme),
        ) {
            content()
        }
    }
}
