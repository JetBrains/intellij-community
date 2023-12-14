package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.JBColor
import com.intellij.util.ui.DirProvider
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.jewel.bridge.bridgePainterProvider
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.readFromLaF
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeOrDefault
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeWithFallbacks
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveColorsOrUnspecified
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValues
import org.jetbrains.jewel.bridge.retrieveIntAsDpOrUnspecified
import org.jetbrains.jewel.bridge.retrieveTextStyle
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.DefaultComponentStyling
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.CheckboxColors
import org.jetbrains.jewel.ui.component.styling.CheckboxIcons
import org.jetbrains.jewel.ui.component.styling.CheckboxMetrics
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.component.styling.ChipColors
import org.jetbrains.jewel.ui.component.styling.ChipMetrics
import org.jetbrains.jewel.ui.component.styling.ChipStyle
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import org.jetbrains.jewel.ui.component.styling.DividerMetrics
import org.jetbrains.jewel.ui.component.styling.DividerStyle
import org.jetbrains.jewel.ui.component.styling.DropdownColors
import org.jetbrains.jewel.ui.component.styling.DropdownIcons
import org.jetbrains.jewel.ui.component.styling.DropdownMetrics
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.GroupHeaderColors
import org.jetbrains.jewel.ui.component.styling.GroupHeaderMetrics
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarColors
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarMetrics
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.LazyTreeColors
import org.jetbrains.jewel.ui.component.styling.LazyTreeIcons
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkIcons
import org.jetbrains.jewel.ui.component.styling.LinkMetrics
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkTextStyles
import org.jetbrains.jewel.ui.component.styling.MenuColors
import org.jetbrains.jewel.ui.component.styling.MenuIcons
import org.jetbrains.jewel.ui.component.styling.MenuItemColors
import org.jetbrains.jewel.ui.component.styling.MenuItemMetrics
import org.jetbrains.jewel.ui.component.styling.MenuMetrics
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.RadioButtonColors
import org.jetbrains.jewel.ui.component.styling.RadioButtonIcons
import org.jetbrains.jewel.ui.component.styling.RadioButtonMetrics
import org.jetbrains.jewel.ui.component.styling.RadioButtonStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.SliderColors
import org.jetbrains.jewel.ui.component.styling.SliderMetrics
import org.jetbrains.jewel.ui.component.styling.SliderStyle
import org.jetbrains.jewel.ui.component.styling.SubmenuMetrics
import org.jetbrains.jewel.ui.component.styling.TabColors
import org.jetbrains.jewel.ui.component.styling.TabContentAlpha
import org.jetbrains.jewel.ui.component.styling.TabIcons
import org.jetbrains.jewel.ui.component.styling.TabMetrics
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaColors
import org.jetbrains.jewel.ui.component.styling.TextAreaMetrics
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldColors
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.skiko.DependsOnJBR
import javax.swing.UIManager
import kotlin.time.Duration.Companion.milliseconds

private val logger = Logger.getInstance("JewelIntUiBridge")

internal val uiDefaults
    get() = UIManager.getDefaults()

private val iconsBasePath
    get() = DirProvider().dir()

@OptIn(DependsOnJBR::class)
internal suspend fun createBridgeThemeDefinition(): ThemeDefinition {
    val textStyle = retrieveTextStyle("Label.font", "Label.foreground")
    return createBridgeThemeDefinition(textStyle)
}

internal fun createBridgeThemeDefinition(textStyle: TextStyle): ThemeDefinition {
    val isDark = !JBColor.isBright()

    logger.debug("Obtaining theme definition from Swing...")

    return ThemeDefinition(
        isDark = isDark,
        globalColors = GlobalColors.readFromLaF(),
        colorPalette = ThemeColorPalette.readFromLaF(),
        iconData = ThemeIconData.readFromLaF(),
        globalMetrics = GlobalMetrics.readFromLaF(),
        defaultTextStyle = textStyle,
        contentColor = JBColor.foreground().toComposeColor(),
    )
}

@OptIn(DependsOnJBR::class)
internal suspend fun createBridgeComponentStyling(
    theme: ThemeDefinition,
) =
    createBridgeComponentStyling(
        theme = theme,
        textFieldTextStyle = retrieveTextStyle("TextField.font", "TextField.foreground"),
        textAreaTextStyle = retrieveTextStyle("TextArea.font", "TextArea.foreground"),
        dropdownTextStyle = retrieveTextStyle("ComboBox.font"),
        linkTextStyle = retrieveTextStyle("Label.font"),
    )

internal fun createBridgeComponentStyling(
    theme: ThemeDefinition,
    textFieldTextStyle: TextStyle,
    textAreaTextStyle: TextStyle,
    dropdownTextStyle: TextStyle,
    linkTextStyle: TextStyle,
): ComponentStyling {
    logger.debug("Obtaining Int UI component styling from Swing...")

    val textFieldStyle = readTextFieldStyle(textFieldTextStyle)
    val menuStyle = readMenuStyle()

    return DefaultComponentStyling(
        checkboxStyle = readCheckboxStyle(),
        chipStyle = readChipStyle(),
        circularProgressStyle = readCircularProgressStyle(theme.isDark),
        defaultButtonStyle = readDefaultButtonStyle(),
        defaultDropdownStyle = readDefaultDropdownStyle(menuStyle, dropdownTextStyle),
        defaultTabStyle = readDefaultTabStyle(),
        dividerStyle = readDividerStyle(),
        editorTabStyle = readEditorTabStyle(),
        groupHeaderStyle = readGroupHeaderStyle(),
        horizontalProgressBarStyle = readHorizontalProgressBarStyle(),
        iconButtonStyle = readIconButtonStyle(),
        lazyTreeStyle = readLazyTreeStyle(),
        linkStyle = readLinkStyle(linkTextStyle),
        menuStyle = menuStyle,
        outlinedButtonStyle = readOutlinedButtonStyle(),
        radioButtonStyle = readRadioButtonStyle(),
        scrollbarStyle = readScrollbarStyle(theme.isDark),
        sliderStyle = readSliderStyle(theme.isDark),
        textAreaStyle = readTextAreaStyle(textAreaTextStyle, textFieldStyle.metrics),
        textFieldStyle = textFieldStyle,
        tooltipStyle = readTooltipStyle(),
        undecoratedDropdownStyle = readUndecoratedDropdownStyle(menuStyle, dropdownTextStyle),
    )
}

private fun readDefaultButtonStyle(): ButtonStyle {
    val normalBackground = listOf(
        JBUI.CurrentTheme.Button.defaultButtonColorStart().toComposeColor(),
        JBUI.CurrentTheme.Button.defaultButtonColorEnd().toComposeColor(),
    ).createVerticalBrush()

    val normalContent = retrieveColorOrUnspecified("Button.default.foreground")

    val normalBorder = listOf(
        JBUI.CurrentTheme.Button.buttonOutlineColorStart(true).toComposeColor(),
        JBUI.CurrentTheme.Button.buttonOutlineColorEnd(true).toComposeColor(),
    ).createVerticalBrush()

    val colors =
        ButtonColors(
            background = normalBackground,
            backgroundDisabled = SolidColor(Color.Transparent),
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("Button.disabledText"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = normalBorder,
            borderDisabled = SolidColor(JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor()),
            borderFocused = SolidColor(retrieveColorOrUnspecified("Button.default.focusedBorderColor")),
            borderPressed = normalBorder,
            borderHovered = normalBorder,
        )

    return ButtonStyle(
        colors = colors,
        metrics = ButtonMetrics(
            cornerSize = retrieveArcAsCornerSizeWithFallbacks("Button.default.arc", "Button.arc"),
            padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            borderWidth = DarculaUIUtil.LW.dp,
        ),
    )
}

private fun readOutlinedButtonStyle(): ButtonStyle {
    val normalBackground = listOf(
        JBUI.CurrentTheme.Button.buttonColorStart().toComposeColor(),
        JBUI.CurrentTheme.Button.buttonColorEnd().toComposeColor(),
    ).createVerticalBrush()

    val normalContent = retrieveColorOrUnspecified("Button.foreground")

    val normalBorder = listOf(
        JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
        JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false).toComposeColor(),
    ).createVerticalBrush()

    val colors = ButtonColors(
        background = normalBackground,
        backgroundDisabled = SolidColor(Color.Transparent),
        backgroundFocused = normalBackground,
        backgroundPressed = normalBackground,
        backgroundHovered = normalBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("Button.disabledText"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        border = normalBorder,
        borderDisabled = SolidColor(JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor()),
        borderFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
        borderPressed = normalBorder,
        borderHovered = normalBorder,
    )

    return ButtonStyle(
        colors = colors,
        metrics =
        ButtonMetrics(
            cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
            padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            borderWidth = DarculaUIUtil.LW.dp,
        ),
    )
}

private fun readCheckboxStyle(): CheckboxStyle {
    val textColor = retrieveColorOrUnspecified("CheckBox.foreground")
    val colors = CheckboxColors(
        content = textColor,
        contentDisabled = retrieveColorOrUnspecified("CheckBox.disabledText"),
        contentSelected = textColor,
    )

    return CheckboxStyle(
        colors = colors,
        metrics = CheckboxMetrics(
            checkboxSize = DarculaCheckBoxUI().defaultIcon.let { DpSize(it.iconWidth.dp, it.iconHeight.dp) },
            checkboxCornerSize = CornerSize(3.dp), // See DarculaCheckBoxUI#drawCheckIcon
            outlineSize = DpSize(15.dp, 15.dp), // Extrapolated from SVG
            outlineOffset = DpOffset(2.5.dp, 1.5.dp), // Extrapolated from SVG
            iconContentGap = 5.dp, // See DarculaCheckBoxUI#textIconGap
        ),
        icons = CheckboxIcons(checkbox = bridgePainterProvider("${iconsBasePath}checkBox.svg")),
    )
}

// Note: there isn't a chip spec, nor a chip UI, so we're deriving this from the
// styling defined in com.intellij.ide.ui.experimental.meetNewUi.MeetNewUiButton
// To note:
//  1. There is no real disabled state, we're making it sort of up
//  2. Chips can be used as buttons (see run configs) or as radio buttons (see MeetNewUi)
//  3. We also have a toggleable version because why not
private fun readChipStyle(): ChipStyle {
    val normalBackground =
        retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground")
            .createVerticalBrush()
    val normalContent = retrieveColorOrUnspecified("Label.foreground")
    val normalBorder = retrieveColorOrUnspecified("Button.startBorderColor")
    val disabledBorder = retrieveColorOrUnspecified("Button.disabledBorderColor")
    val selectedBorder = retrieveColorOrUnspecified("Component.focusColor")

    val colors = ChipColors(
        background = normalBackground,
        backgroundDisabled = normalBackground,
        backgroundFocused = normalBackground,
        backgroundPressed = normalBackground,
        backgroundHovered = normalBackground,
        backgroundSelected = normalBackground,
        backgroundSelectedDisabled = normalBackground,
        backgroundSelectedPressed = normalBackground,
        backgroundSelectedFocused = normalBackground,
        backgroundSelectedHovered = normalBackground,
        content = normalContent,
        contentDisabled = normalContent,
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        contentSelected = normalContent,
        contentSelectedDisabled = normalContent,
        contentSelectedPressed = normalContent,
        contentSelectedFocused = normalContent,
        contentSelectedHovered = normalContent,
        border = normalBorder,
        borderDisabled = disabledBorder,
        borderFocused = normalBorder,
        borderPressed = normalBorder,
        borderHovered = normalBorder,
        borderSelected = selectedBorder,
        borderSelectedDisabled = disabledBorder,
        borderSelectedPressed = selectedBorder,
        borderSelectedFocused = selectedBorder,
        borderSelectedHovered = selectedBorder,
    )

    return ChipStyle(
        colors = colors,
        metrics = ChipMetrics(
            cornerSize = CornerSize(6.dp),
            padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            borderWidth = 1.dp,
            borderWidthSelected = 2.dp,
        ),
    )
}

private fun readDividerStyle() =
    DividerStyle(
        color = retrieveColorOrUnspecified("Borders.color"),
        metrics = DividerMetrics.defaults(),
    )

private fun readDefaultDropdownStyle(
    menuStyle: MenuStyle,
    dropdownTextStyle: TextStyle,
): DropdownStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.nonEditableBackground")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")
    val normalBorder = retrieveColorOrUnspecified("Component.borderColor")
    val focusedBorder = retrieveColorOrUnspecified("Component.focusedBorderColor")

    val colors = DropdownColors(
        background = normalBackground,
        backgroundDisabled = retrieveColorOrUnspecified("ComboBox.disabledBackground"),
        backgroundFocused = normalBackground,
        backgroundPressed = normalBackground,
        backgroundHovered = normalBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("ComboBox.disabledForeground"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        border = normalBorder,
        borderDisabled = retrieveColorOrUnspecified("Component.disabledBorderColor"),
        borderFocused = focusedBorder,
        borderPressed = focusedBorder,
        borderHovered = normalBorder,
        iconTint = Color.Unspecified,
        iconTintDisabled = Color.Unspecified,
        iconTintFocused = Color.Unspecified,
        iconTintPressed = Color.Unspecified,
        iconTintHovered = Color.Unspecified,
    )

    val arrowWidth = DarculaUIUtil.ARROW_BUTTON_WIDTH.dp
    return DropdownStyle(
        colors = colors,
        metrics = DropdownMetrics(
            arrowMinSize = DpSize(arrowWidth, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp + arrowWidth, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            cornerSize = CornerSize(DarculaUIUtil.COMPONENT_ARC.dp),
            contentPadding = retrieveInsetsAsPaddingValues("ComboBox.padding"),
            borderWidth = DarculaUIUtil.BW.dp,
        ),
        icons = DropdownIcons(chevronDown = bridgePainterProvider("general/chevron-down.svg")),
        textStyle = dropdownTextStyle,
        menuStyle = menuStyle,
    )
}

private fun readUndecoratedDropdownStyle(
    menuStyle: MenuStyle,
    dropdownTextStyle: TextStyle,
): DropdownStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.nonEditableBackground")
    val hoverBackground = retrieveColorOrUnspecified("MainToolbar.Dropdown.transparentHoverBackground")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")

    val colors = DropdownColors(
        background = normalBackground,
        backgroundDisabled = retrieveColorOrUnspecified("ComboBox.disabledBackground"),
        backgroundFocused = normalBackground,
        backgroundPressed = normalBackground,
        backgroundHovered = hoverBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("ComboBox.disabledForeground"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        border = Color.Transparent,
        borderDisabled = Color.Transparent,
        borderFocused = Color.Transparent,
        borderPressed = Color.Transparent,
        borderHovered = Color.Transparent,
        iconTint = Color.Unspecified,
        iconTintDisabled = Color.Unspecified,
        iconTintFocused = Color.Unspecified,
        iconTintPressed = Color.Unspecified,
        iconTintHovered = Color.Unspecified,
    )

    val arrowWidth = DarculaUIUtil.ARROW_BUTTON_WIDTH.dp
    return DropdownStyle(
        colors = colors,
        metrics = DropdownMetrics(
            arrowMinSize = DpSize(arrowWidth, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp + arrowWidth, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            cornerSize = CornerSize(JBUI.CurrentTheme.MainToolbar.Dropdown.hoverArc().dp),
            contentPadding = JBUI.CurrentTheme.MainToolbar.Dropdown.borderInsets().toPaddingValues(),
            borderWidth = 0.dp,
        ),
        icons = DropdownIcons(chevronDown = bridgePainterProvider("general/chevron-down.svg")),
        textStyle = dropdownTextStyle,
        menuStyle = menuStyle,
    )
}

private fun readGroupHeaderStyle() =
    GroupHeaderStyle(
        colors = GroupHeaderColors(divider = retrieveColorOrUnspecified("Separator.separatorColor")),
        metrics = GroupHeaderMetrics(
            dividerThickness = 1.dp, // see DarculaSeparatorUI
            indent = 1.dp, // see DarculaSeparatorUI
        ),
    )

private fun readHorizontalProgressBarStyle() =
    HorizontalProgressBarStyle(
        colors = HorizontalProgressBarColors(
            track = retrieveColorOrUnspecified("ProgressBar.trackColor"),
            progress = retrieveColorOrUnspecified("ProgressBar.progressColor"),
            indeterminateBase = retrieveColorOrUnspecified("ProgressBar.indeterminateStartColor"),
            indeterminateHighlight = retrieveColorOrUnspecified("ProgressBar.indeterminateEndColor"),
        ),
        metrics = HorizontalProgressBarMetrics(
            cornerSize = CornerSize(100),
            minHeight = 4.dp, // See DarculaProgressBarUI.DEFAULT_WIDTH
            // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT,
            // DarculaProgressBarUI.REPAINT_INTERVAL_DEFAULT,
            // and the "step" constant in DarculaProgressBarUI#paintIndeterminate
            indeterminateHighlightWidth = (800 / 50 * 6).dp,
        ),
        indeterminateCycleDuration = 800.milliseconds, // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT
    )

private fun readLinkStyle(
    linkTextStyle: TextStyle,
): LinkStyle {
    val normalContent = retrieveColorOrUnspecified("Link.activeForeground")
        .takeOrElse { retrieveColorOrUnspecified("Link.activeForeground") }

    val colors =
        LinkColors(
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("Link.disabledForeground")
                .takeOrElse { retrieveColorOrUnspecified("Label.disabledForeground") },
            contentFocused = normalContent,
            contentPressed = retrieveColorOrUnspecified("Link.pressedForeground")
                .takeOrElse { retrieveColorOrUnspecified("link.pressed.foreground") },
            contentHovered = retrieveColorOrUnspecified("Link.hoverForeground")
                .takeOrElse { retrieveColorOrUnspecified("link.hover.foreground") },
            contentVisited = retrieveColorOrUnspecified("Link.visitedForeground")
                .takeOrElse { retrieveColorOrUnspecified("link.visited.foreground") },
        )

    return LinkStyle(
        colors = colors,
        metrics = LinkMetrics(
            focusHaloCornerSize = retrieveArcAsCornerSizeOrDefault(
                key = "ide.link.button.focus.round.arc",
                default = CornerSize(4.dp),
            ),
            textIconGap = 4.dp,
            iconSize = DpSize(16.dp, 16.dp),
        ),
        icons = LinkIcons(
            dropdownChevron = bridgePainterProvider("general/chevron-down.svg"),
            externalLink = bridgePainterProvider("ide/external_link_arrow.svg"),
        ),
        textStyles = LinkTextStyles(
            normal = linkTextStyle,
            disabled = linkTextStyle,
            focused = linkTextStyle,
            pressed = linkTextStyle,
            hovered = linkTextStyle,
            visited = linkTextStyle,
        ),
    )
}

private fun readMenuStyle(): MenuStyle {
    val backgroundSelected = retrieveColorOrUnspecified("MenuItem.selectionBackground")
    val foregroundSelected = retrieveColorOrUnspecified("MenuItem.selectionForeground")
    val keybindingTint = retrieveColorOrUnspecified("MenuItem.acceleratorForeground")
    val keybindingTintSelected = Color.Unspecified

    val colors = MenuColors(
        background = retrieveColorOrUnspecified("PopupMenu.background"),
        border = retrieveColorOrUnspecified("Popup.borderColor")
            .takeOrElse { retrieveColorOrUnspecified("Popup.Border.color") },
        shadow = Color.Black.copy(alpha = .6f),
        itemColors = MenuItemColors(
            background = retrieveColorOrUnspecified("MenuItem.background"),
            backgroundDisabled = retrieveColorOrUnspecified("MenuItem.disabledBackground"),
            backgroundFocused = backgroundSelected,
            backgroundPressed = backgroundSelected,
            backgroundHovered = backgroundSelected,
            content = retrieveColorOrUnspecified("PopupMenu.foreground"),
            contentDisabled = retrieveColorOrUnspecified("PopupMenu.disabledForeground"),
            contentFocused = foregroundSelected,
            contentPressed = foregroundSelected,
            contentHovered = foregroundSelected,
            iconTint = Color.Unspecified,
            iconTintDisabled = Color.Unspecified,
            iconTintFocused = Color.Unspecified,
            iconTintPressed = Color.Unspecified,
            iconTintHovered = Color.Unspecified,
            keybindingTint = keybindingTint,
            keybindingTintDisabled = keybindingTint,
            keybindingTintFocused = keybindingTintSelected,
            keybindingTintPressed = keybindingTintSelected,
            keybindingTintHovered = keybindingTintSelected,
            separator = retrieveColorOrUnspecified("Menu.separatorColor"),
        ),
    )

    return MenuStyle(
        isDark = !JBColor.isBright(),
        colors = colors,
        metrics = MenuMetrics(
            cornerSize = CornerSize(IdeaPopupMenuUI.CORNER_RADIUS.dp),
            menuMargin = PaddingValues(0.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            offset = DpOffset(0.dp, 2.dp),
            shadowSize = 12.dp,
            borderWidth = retrieveIntAsDpOrUnspecified("Popup.borderWidth").takeOrElse { 1.dp },
            itemMetrics = MenuItemMetrics(
                selectionCornerSize = CornerSize(JBUI.CurrentTheme.PopupMenu.Selection.ARC.dp),
                outerPadding = PaddingValues(horizontal = 6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                keybindingsPadding = PaddingValues(start = 36.dp),
                separatorPadding = PaddingValues(
                    horizontal = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.withToEdge")
                        .takeOrElse { 0.dp },
                    vertical = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeIndent")
                        .takeOrElse { 0.dp },
                ),
                separatorThickness = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeWidth")
                    .takeOrElse { 0.dp },
                iconSize = 16.dp,
            ),
            submenuMetrics = SubmenuMetrics(offset = DpOffset(0.dp, (-8).dp)),
        ),
        icons = MenuIcons(submenuChevron = bridgePainterProvider("general/chevron-right.svg")),
    )
}

private fun readRadioButtonStyle(): RadioButtonStyle {
    val normalContent = retrieveColorOrUnspecified("RadioButton.foreground")
    val disabledContent = retrieveColorOrUnspecified("RadioButton.disabledText")
    val colors = RadioButtonColors(
        content = normalContent,
        contentHovered = normalContent,
        contentDisabled = disabledContent,
        contentSelected = normalContent,
        contentSelectedHovered = normalContent,
        contentSelectedDisabled = disabledContent,
    )

    return RadioButtonStyle(
        colors = colors,
        metrics = RadioButtonMetrics(
            radioButtonSize = DpSize(19.dp, 19.dp),
            iconContentGap = retrieveIntAsDpOrUnspecified("RadioButton.textIconGap")
                .takeOrElse { 4.dp },
        ),
        icons = RadioButtonIcons(radioButton = bridgePainterProvider("${iconsBasePath}radio.svg")),
    )
}

private fun readScrollbarStyle(isDark: Boolean) =
    ScrollbarStyle(
        colors = ScrollbarColors(
            // See ScrollBarPainter.THUMB_OPAQUE_BACKGROUND
            thumbBackground = retrieveColorOrUnspecified("ScrollBar.Mac.Transparent.thumbColor")
                .let { if (it.alpha == 0f) Color.Unspecified else it } // See https://github.com/JetBrains/jewel/issues/259
                .takeOrElse { if (isDark) Color(0x59808080) else Color(0x33000000) },
            // See ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND
            thumbBackgroundHovered = retrieveColorOrUnspecified("ScrollBar.Mac.Transparent.hoverThumbColor")
                .let { if (it.alpha == 0f) Color.Unspecified else it } // See https://github.com/JetBrains/jewel/issues/259
                .takeOrElse { if (isDark) Color(0x8C808080) else Color(0x80000000) },
        ),
        metrics = ScrollbarMetrics(
            thumbCornerSize = CornerSize(100),
            thumbThickness = 8.dp,
            minThumbLength = 16.dp,
            trackPadding = PaddingValues(start = 7.dp, end = 3.dp),
        ),
        hoverDuration = 300.milliseconds,
    )

private fun readSliderStyle(dark: Boolean): SliderStyle {
    // There are no values for sliders in IntUi, so we're essentially reusing the
    // standalone colors logic, reading the palette values (and falling back to
    // hardcoded defaults).
    val colors = if (dark) SliderColors.dark() else SliderColors.light()
    return SliderStyle(colors, SliderMetrics.defaults(), CircleShape)
}

private fun readTextAreaStyle(textStyle: TextStyle, metrics: TextFieldMetrics): TextAreaStyle {
    val normalBackground = retrieveColorOrUnspecified("TextArea.background")
    val normalContent = retrieveColorOrUnspecified("TextArea.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextArea.caretForeground")

    val colors = TextAreaColors(
        background = normalBackground,
        backgroundDisabled = retrieveColorOrUnspecified("TextArea.disabledBackground"),
        backgroundFocused = normalBackground,
        backgroundPressed = normalBackground,
        backgroundHovered = normalBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("TextArea.inactiveForeground"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        border = normalBorder,
        borderDisabled = DarculaUIUtil.getOutlineColor(false, false).toComposeColor(),
        borderFocused = focusedBorder,
        borderPressed = focusedBorder,
        borderHovered = normalBorder,
        caret = normalCaret,
        caretDisabled = normalCaret,
        caretFocused = normalCaret,
        caretPressed = normalCaret,
        caretHovered = normalCaret,
        placeholder = NamedColorUtil.getInactiveTextColor().toComposeColor(),
    )

    return TextAreaStyle(
        colors = colors,
        metrics = TextAreaMetrics(
            cornerSize = metrics.cornerSize,
            contentPadding = metrics.contentPadding,
            minSize = metrics.minSize,
            borderWidth = metrics.borderWidth,
        ),
        textStyle = textStyle,
    )
}

private fun readTextFieldStyle(textFieldStyle: TextStyle): TextFieldStyle {
    val normalBackground = retrieveColorOrUnspecified("TextField.background")
    val normalContent = retrieveColorOrUnspecified("TextField.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextField.caretForeground")

    val colors = TextFieldColors(
        background = normalBackground,
        backgroundDisabled = retrieveColorOrUnspecified("TextField.disabledBackground"),
        backgroundFocused = normalBackground,
        backgroundPressed = normalBackground,
        backgroundHovered = normalBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("TextField.inactiveForeground"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        border = normalBorder,
        borderDisabled = DarculaUIUtil.getOutlineColor(false, false).toComposeColor(),
        borderFocused = focusedBorder,
        borderPressed = focusedBorder,
        borderHovered = normalBorder,
        caret = normalCaret,
        caretDisabled = normalCaret,
        caretFocused = normalCaret,
        caretPressed = normalCaret,
        caretHovered = normalCaret,
        placeholder = NamedColorUtil.getInactiveTextColor().toComposeColor(),
    )

    return TextFieldStyle(
        colors = colors,
        metrics = TextFieldMetrics(
            cornerSize = CornerSize(DarculaUIUtil.COMPONENT_ARC.dp),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            borderWidth = DarculaUIUtil.LW.dp,
        ),
        textStyle = textFieldStyle,
    )
}

private fun readLazyTreeStyle(): LazyTreeStyle {
    val normalContent = retrieveColorOrUnspecified("Tree.foreground")
    val selectedContent = retrieveColorOrUnspecified("Tree.selectionForeground")
    val selectedElementBackground = retrieveColorOrUnspecified("Tree.selectionBackground")
    val inactiveSelectedElementBackground = retrieveColorOrUnspecified("Tree.selectionInactiveBackground")

    val colors = LazyTreeColors(
        content = normalContent,
        contentFocused = normalContent,
        contentSelected = selectedContent,
        contentSelectedFocused = selectedContent,
        elementBackgroundFocused = Color.Transparent,
        elementBackgroundSelected = inactiveSelectedElementBackground,
        elementBackgroundSelectedFocused = selectedElementBackground,
    )

    val chevronCollapsed = bridgePainterProvider("general/chevron-right.svg")
    val chevronExpanded = bridgePainterProvider("general/chevron-down.svg")

    val leftIndent = retrieveIntAsDpOrUnspecified("Tree.leftChildIndent").takeOrElse { 7.dp }
    val rightIndent = retrieveIntAsDpOrUnspecified("Tree.rightChildIndent").takeOrElse { 11.dp }
    return LazyTreeStyle(
        colors = colors,
        metrics = LazyTreeMetrics(
            indentSize = leftIndent + rightIndent,
            elementBackgroundCornerSize = CornerSize(JBUI.CurrentTheme.Tree.ARC.dp / 2),
            elementPadding = PaddingValues(horizontal = 12.dp),
            elementContentPadding = PaddingValues(4.dp),
            elementMinHeight = retrieveIntAsDpOrUnspecified("Tree.rowHeight").takeOrElse { 24.dp },
            chevronContentGap = 2.dp, // See com.intellij.ui.tree.ui.ClassicPainter.GAP
        ),
        icons = LazyTreeIcons(
            chevronCollapsed = chevronCollapsed,
            chevronExpanded = chevronExpanded,
            chevronSelectedCollapsed = chevronCollapsed,
            chevronSelectedExpanded = chevronExpanded,
        ),
    )
}

// See com.intellij.ui.tabs.impl.themes.DefaultTabTheme
private fun readDefaultTabStyle(): TabStyle {
    val normalBackground = JBUI.CurrentTheme.DefaultTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.DefaultTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors = TabColors(
        background = normalBackground,
        backgroundDisabled = normalBackground,
        backgroundPressed = selectedBackground,
        backgroundHovered = JBUI.CurrentTheme.DefaultTabs.hoverBackground().toComposeColor(),
        backgroundSelected = selectedBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("TabbedPane.disabledForeground"),
        contentPressed = normalContent,
        contentHovered = normalContent,
        contentSelected = normalContent,
        underline = Color.Transparent,
        underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
        underlinePressed = selectedUnderline,
        underlineHovered = Color.Transparent,
        underlineSelected = selectedUnderline,
    )

    return TabStyle(
        colors = colors,
        metrics = TabMetrics(
            underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight")
                .takeOrElse { 2.dp },
            tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
            closeContentGap = 4.dp,
            tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight").takeOrElse { 24.dp },
        ),
        icons = TabIcons(close = bridgePainterProvider("expui/general/closeSmall.svg")),
        contentAlpha = TabContentAlpha(
            iconNormal = 1f,
            iconDisabled = 1f,
            iconPressed = 1f,
            iconHovered = 1f,
            iconSelected = 1f,
            labelNormal = 1f,
            labelDisabled = 1f,
            labelPressed = 1f,
            labelHovered = 1f,
            labelSelected = 1f,
        ),
    )
}

private fun readEditorTabStyle(): TabStyle {
    val normalBackground = JBUI.CurrentTheme.EditorTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.EditorTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors = TabColors(
        background = normalBackground,
        backgroundDisabled = normalBackground,
        backgroundPressed = selectedBackground,
        backgroundHovered = JBUI.CurrentTheme.EditorTabs.hoverBackground().toComposeColor(),
        backgroundSelected = selectedBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("TabbedPane.disabledForeground"),
        contentPressed = normalContent,
        contentHovered = normalContent,
        contentSelected = normalContent,
        underline = Color.Transparent,
        underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
        underlinePressed = selectedUnderline,
        underlineHovered = Color.Transparent,
        underlineSelected = selectedUnderline,
    )

    return TabStyle(
        colors = colors,
        metrics = TabMetrics(
            underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight")
                .takeOrElse { 2.dp },
            tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
            closeContentGap = 4.dp,
            tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight")
                .takeOrElse { 24.dp },
        ),
        icons = TabIcons(close = bridgePainterProvider("expui/general/closeSmall.svg")),
        contentAlpha = TabContentAlpha(
            iconNormal = .7f,
            iconDisabled = .7f,
            iconPressed = 1f,
            iconHovered = 1f,
            iconSelected = 1f,
            labelNormal = .7f,
            labelDisabled = .7f,
            labelPressed = 1f,
            labelHovered = 1f,
            labelSelected = 1f,
        ),
    )
}

private fun readCircularProgressStyle(isDark: Boolean) =
    CircularProgressStyle(
        frameTime = 125.milliseconds,
        color = retrieveColorOrUnspecified("ProgressIcon.color")
            .takeOrElse { if (isDark) Color(0xFF6F737A) else Color(0xFFA8ADBD) },
    )

private fun readTooltipStyle() =
    TooltipStyle(
        metrics = TooltipMetrics.defaults(),
        colors = TooltipColors(
            content = retrieveColorOrUnspecified("ToolTip.foreground"),
            background = retrieveColorOrUnspecified("ToolTip.background"),
            border = retrieveColorOrUnspecified("ToolTip.borderColor"),
            shadow = retrieveColorOrUnspecified("Notification.Shadow.bottom1Color"),
        ),
    )

private fun readIconButtonStyle(): IconButtonStyle =
    IconButtonStyle(
        metrics = IconButtonMetrics(
            cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
            borderWidth = 1.dp,
            padding = PaddingValues(0.dp),
            minSize = DpSize(16.dp, 16.dp),
        ),
        colors = IconButtonColors(
            foregroundSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedForeground"),
            background = Color.Unspecified,
            backgroundDisabled = Color.Unspecified,
            backgroundSelected = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
            backgroundSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedBackground"),
            backgroundFocused = Color.Unspecified,
            backgroundPressed = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
            backgroundHovered = retrieveColorOrUnspecified("ActionButton.hoverBackground"),
            border = Color.Unspecified,
            borderDisabled = Color.Unspecified,
            borderSelected = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
            borderSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedBackground"),
            borderFocused = Color.Unspecified,
            borderPressed = retrieveColorOrUnspecified("ActionButton.pressedBorderColor"),
            borderHovered = retrieveColorOrUnspecified("ActionButton.hoverBorderColor"),
        ),
    )
