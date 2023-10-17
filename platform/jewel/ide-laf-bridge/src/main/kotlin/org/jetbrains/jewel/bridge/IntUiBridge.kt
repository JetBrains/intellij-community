package org.jetbrains.jewel.bridge

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.util.ui.DirProvider
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.StatusText
import org.jetbrains.jewel.IntelliJComponentStyling
import org.jetbrains.jewel.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.intui.standalone.styling.IntUiButtonColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiButtonMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiButtonStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiCheckboxColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiCheckboxIcons
import org.jetbrains.jewel.intui.standalone.styling.IntUiCheckboxMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiCheckboxStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiChipColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiChipMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiChipStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiCircularProgressStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiDividerMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiDividerStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownIcons
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiDropdownStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiGroupHeaderColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiGroupHeaderMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiGroupHeaderStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiHorizontalProgressBarColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiHorizontalProgressBarMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiHorizontalProgressBarStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiIconButtonColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiIconButtonMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiIconButtonStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiLabelledTextFieldColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiLabelledTextFieldMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiLabelledTextFieldStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiLabelledTextFieldTextStyles
import org.jetbrains.jewel.intui.standalone.styling.IntUiLazyTreeColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiLazyTreeIcons
import org.jetbrains.jewel.intui.standalone.styling.IntUiLazyTreeMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiLazyTreeStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiLinkColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiLinkIcons
import org.jetbrains.jewel.intui.standalone.styling.IntUiLinkMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiLinkStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiLinkTextStyles
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuIcons
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuItemColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuItemMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiMenuStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiRadioButtonColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiRadioButtonIcons
import org.jetbrains.jewel.intui.standalone.styling.IntUiRadioButtonMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiRadioButtonStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiScrollbarColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiScrollbarMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiScrollbarStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiSubmenuMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiTabColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiTabContentAlpha
import org.jetbrains.jewel.intui.standalone.styling.IntUiTabIcons
import org.jetbrains.jewel.intui.standalone.styling.IntUiTabMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiTabStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextAreaColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextAreaMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextAreaStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextFieldColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextFieldMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiTextFieldStyle
import org.jetbrains.jewel.intui.standalone.styling.IntUiTooltipColors
import org.jetbrains.jewel.intui.standalone.styling.IntUiTooltipMetrics
import org.jetbrains.jewel.intui.standalone.styling.IntUiTooltipStyle
import org.jetbrains.jewel.styling.InputFieldStyle
import org.jetbrains.skiko.DependsOnJBR
import javax.swing.UIManager
import kotlin.time.Duration.Companion.milliseconds

private val logger = Logger.getInstance("JewelIntUiBridge")

internal val uiDefaults
    get() = UIManager.getDefaults()

@OptIn(DependsOnJBR::class)
internal suspend fun createBridgeIntUiDefinition(): IntUiThemeDefinition {
    val textStyle = retrieveTextStyle("Label.font", "Label.foreground")
    return createBridgeIntUiDefinition(textStyle)
}

internal fun createBridgeIntUiDefinition(textStyle: TextStyle): IntUiThemeDefinition {
    val isDark = !JBColor.isBright()

    logger.debug("Obtaining Int UI theme definition from Swing...")

    return IntUiThemeDefinition(
        isDark = isDark,
        globalColors = BridgeGlobalColors.readFromLaF(),
        colorPalette = BridgeThemeColorPalette.readFromLaF(),
        iconData = BridgeIconData.readFromLaF(),
        globalMetrics = BridgeGlobalMetrics.readFromLaF(),
        defaultTextStyle = textStyle,
        contentColor = JBColor.foreground().toComposeColor(),
    )
}

@OptIn(DependsOnJBR::class)
internal suspend fun createSwingIntUiComponentStyling(
    theme: IntUiThemeDefinition,
): IntelliJComponentStyling = createSwingIntUiComponentStyling(
    theme = theme,
    textAreaTextStyle = retrieveTextStyle("TextArea.font", "TextArea.foreground"),
    textFieldTextStyle = retrieveTextStyle("TextField.font", "TextField.foreground"),
    dropdownTextStyle = retrieveTextStyle("ComboBox.font"),
    labelTextStyle = retrieveTextStyle("Label.font"),
    linkTextStyle = retrieveTextStyle("Label.font"),
)

internal fun createSwingIntUiComponentStyling(
    theme: IntUiThemeDefinition,
    textFieldTextStyle: TextStyle,
    textAreaTextStyle: TextStyle,
    dropdownTextStyle: TextStyle,
    labelTextStyle: TextStyle,
    linkTextStyle: TextStyle,
): IntelliJComponentStyling {
    logger.debug("Obtaining Int UI component styling from Swing...")

    val textFieldStyle = readTextFieldStyle(textFieldTextStyle)
    val menuStyle = readMenuStyle()

    return IntelliJComponentStyling(
        checkboxStyle = readCheckboxStyle(),
        chipStyle = readChipStyle(),
        defaultButtonStyle = readDefaultButtonStyle(),
        defaultTabStyle = readDefaultTabStyle(),
        dividerStyle = readDividerStyle(),
        dropdownStyle = readDropdownStyle(menuStyle, dropdownTextStyle),
        editorTabStyle = readEditorTabStyle(),
        groupHeaderStyle = readGroupHeaderStyle(),
        horizontalProgressBarStyle = readHorizontalProgressBarStyle(),
        labelledTextFieldStyle = readLabelledTextFieldStyle(textFieldStyle, labelTextStyle),
        lazyTreeStyle = readLazyTreeStyle(),
        linkStyle = readLinkStyle(linkTextStyle),
        menuStyle = menuStyle,
        outlinedButtonStyle = readOutlinedButtonStyle(),
        radioButtonStyle = readRadioButtonStyle(),
        scrollbarStyle = readScrollbarStyle(theme.isDark),
        textAreaStyle = readTextAreaStyle(textAreaTextStyle, textFieldStyle.metrics),
        circularProgressStyle = readCircularProgressStyle(theme.isDark),
        tooltipStyle = readTooltipStyle(),
        textFieldStyle = textFieldStyle,
        iconButtonStyle = readIconButtonStyle(),
    )
}

private fun readDefaultButtonStyle(): IntUiButtonStyle {
    val normalBackground =
        retrieveColorsOrUnspecified(
            "Button.default.startBackground",
            "Button.default.endBackground",
        ).createVerticalBrush()
    val normalContent = retrieveColorOrUnspecified("Button.default.foreground")
    val normalBorder =
        retrieveColorsOrUnspecified("Button.default.startBorderColor", "Button.default.endBorderColor")
            .createVerticalBrush()

    val colors = IntUiButtonColors(
        background = normalBackground,
        backgroundDisabled = SolidColor(Color.Transparent),
        backgroundFocused = normalBackground,
        backgroundPressed = normalBackground,
        backgroundHovered = normalBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("Button.default.disabledText"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        border = normalBorder,
        borderDisabled = SolidColor(retrieveColorOrUnspecified("Button.default.disabledBorderColor")),
        borderFocused = SolidColor(retrieveColorOrUnspecified("Button.default.focusedBorderColor")),
        borderPressed = normalBorder,
        borderHovered = normalBorder,
    )

    return IntUiButtonStyle(
        colors = colors,
        metrics = IntUiButtonMetrics(
            cornerSize = retrieveArcAsCornerSizeWithFallbacks("Button.default.arc", "Button.arc"),
            padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            borderWidth = DarculaUIUtil.LW.dp,
        ),
    )
}

private fun readOutlinedButtonStyle(): IntUiButtonStyle {
    val normalBackground =
        retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground")
            .createVerticalBrush()
    val normalContent = retrieveColorOrUnspecified("Button.foreground")
    val normalBorder =
        retrieveColorsOrUnspecified("Button.startBorderColor", "Button.endBorderColor")
            .createVerticalBrush()

    val colors = IntUiButtonColors(
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
        borderDisabled = SolidColor(retrieveColorOrUnspecified("Button.disabledBorderColor")),
        borderFocused = SolidColor(retrieveColorOrUnspecified("Button.focusedBorderColor")),
        borderPressed = normalBorder,
        borderHovered = normalBorder,
    )

    return IntUiButtonStyle(
        colors = colors,
        metrics = IntUiButtonMetrics(
            cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
            padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            borderWidth = DarculaUIUtil.LW.dp,
        ),
    )
}

private val iconsBasePath
    get() = DirProvider().dir()

private fun readCheckboxStyle(): IntUiCheckboxStyle {
    val background = retrieveColorOrUnspecified("CheckBox.background")
    val textColor = retrieveColorOrUnspecified("CheckBox.foreground")
    val colors = IntUiCheckboxColors(
        checkboxBackground = background,
        checkboxBackgroundDisabled = background,
        checkboxBackgroundSelected = background,
        content = textColor,
        contentDisabled = retrieveColorOrUnspecified("CheckBox.disabledText"),
        contentSelected = textColor,
    )

    return IntUiCheckboxStyle(
        colors = colors,
        metrics = IntUiCheckboxMetrics(
            checkboxSize = DarculaCheckBoxUI().defaultIcon.let { DpSize(it.iconWidth.dp, it.iconHeight.dp) },
            checkboxCornerSize = CornerSize(3.dp), // See DarculaCheckBoxUI#drawCheckIcon
            outlineSize = DpSize(15.dp, 15.dp), // Extrapolated from SVG
            outlineOffset = DpOffset(2.5.dp, 1.5.dp), // Extrapolated from SVG
            iconContentGap = 5.dp, // See DarculaCheckBoxUI#textIconGap
        ),
        icons = IntUiCheckboxIcons(
            checkbox = bridgePainterProvider("${iconsBasePath}checkBox.svg"),
        ),
    )
}

// Note: there isn't a chip spec, nor a chip UI, so we're deriving this from the
// styling defined in com.intellij.ide.ui.experimental.meetNewUi.MeetNewUiButton
// To note:
//  1. There is no real disabled state, we're making it sort of up
//  2. Chips can be used as buttons (see run configs) or as radio buttons (see MeetNewUi)
//  3. We also have a toggleable version because why not
private fun readChipStyle(): IntUiChipStyle {
    val normalBackground =
        retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground")
            .createVerticalBrush()
    val normalContent = retrieveColorOrUnspecified("Label.foreground")
    val normalBorder = retrieveColorOrUnspecified("Button.startBorderColor")
    val disabledBorder = retrieveColorOrUnspecified("Button.disabledBorderColor")
    val selectedBorder = retrieveColorOrUnspecified("Component.focusColor")

    val colors = IntUiChipColors(
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
    return IntUiChipStyle(
        colors = colors,
        metrics = IntUiChipMetrics(
            cornerSize = CornerSize(6.dp),
            padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            borderWidth = 1.dp,
            borderWidthSelected = 2.dp,
        ),
    )
}

private fun readDividerStyle() =
    IntUiDividerStyle(
        color = retrieveColorOrUnspecified("Borders.color"),
        metrics = IntUiDividerMetrics(),
    )

private fun readDropdownStyle(
    menuStyle: IntUiMenuStyle,
    dropdownTextStyle: TextStyle,
): IntUiDropdownStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.nonEditableBackground")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")
    val normalBorder = retrieveColorOrUnspecified("Component.borderColor")
    val focusedBorder = retrieveColorOrUnspecified("Component.focusedBorderColor")

    val colors = IntUiDropdownColors(
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
    return IntUiDropdownStyle(
        colors = colors,
        metrics = IntUiDropdownMetrics(
            arrowMinSize = DpSize(arrowWidth, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            minSize = DpSize(
                DarculaUIUtil.MINIMUM_WIDTH.dp + arrowWidth,
                DarculaUIUtil.MINIMUM_HEIGHT.dp,
            ),
            cornerSize = CornerSize(DarculaUIUtil.COMPONENT_ARC.dp),
            contentPadding = retrieveInsetsAsPaddingValues("ComboBox.padding"),
            borderWidth = DarculaUIUtil.BW.dp,
        ),
        icons = IntUiDropdownIcons(
            chevronDown = bridgePainterProvider("${iconsBasePath}general/chevron-down.svg"),
        ),
        textStyle = dropdownTextStyle,
        menuStyle = menuStyle,
    )
}

private fun readGroupHeaderStyle() = IntUiGroupHeaderStyle(
    colors = IntUiGroupHeaderColors(
        divider = retrieveColorOrUnspecified("Separator.separatorColor"),
    ),
    metrics = IntUiGroupHeaderMetrics(
        dividerThickness = 1.dp, // see DarculaSeparatorUI
        indent = 1.dp, // see DarculaSeparatorUI
    ),
)

private fun readHorizontalProgressBarStyle() = IntUiHorizontalProgressBarStyle(
    colors = IntUiHorizontalProgressBarColors(
        track = retrieveColorOrUnspecified("ProgressBar.trackColor"),
        progress = retrieveColorOrUnspecified("ProgressBar.progressColor"),
        indeterminateBase = retrieveColorOrUnspecified("ProgressBar.indeterminateStartColor"),
        indeterminateHighlight = retrieveColorOrUnspecified("ProgressBar.indeterminateEndColor"),
    ),
    metrics = IntUiHorizontalProgressBarMetrics(
        cornerSize = CornerSize(100),
        minHeight = 4.dp, // See DarculaProgressBarUI.DEFAULT_WIDTH
        // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT, DarculaProgressBarUI.REPAINT_INTERVAL_DEFAULT,
        // and the "step" constant in DarculaProgressBarUI#paintIndeterminate
        indeterminateHighlightWidth = (800 / 50 * 6).dp,
    ),
    indeterminateCycleDuration = 800.milliseconds, // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT
)

private fun readLabelledTextFieldStyle(
    inputFieldStyle: InputFieldStyle,
    labelTextStyle: TextStyle,
): IntUiLabelledTextFieldStyle {
    val colors = IntUiLabelledTextFieldColors(
        background = inputFieldStyle.colors.background,
        backgroundDisabled = inputFieldStyle.colors.backgroundDisabled,
        backgroundFocused = inputFieldStyle.colors.backgroundFocused,
        backgroundPressed = inputFieldStyle.colors.backgroundPressed,
        backgroundHovered = inputFieldStyle.colors.backgroundHovered,
        content = inputFieldStyle.colors.content,
        contentDisabled = inputFieldStyle.colors.contentDisabled,
        contentFocused = inputFieldStyle.colors.contentFocused,
        contentPressed = inputFieldStyle.colors.contentPressed,
        contentHovered = inputFieldStyle.colors.contentHovered,
        border = inputFieldStyle.colors.border,
        borderDisabled = inputFieldStyle.colors.borderDisabled,
        borderFocused = inputFieldStyle.colors.borderFocused,
        borderPressed = inputFieldStyle.colors.borderPressed,
        borderHovered = inputFieldStyle.colors.borderHovered,
        caret = inputFieldStyle.colors.caret,
        caretDisabled = inputFieldStyle.colors.caretDisabled,
        caretFocused = inputFieldStyle.colors.caretFocused,
        caretPressed = inputFieldStyle.colors.caretPressed,
        caretHovered = inputFieldStyle.colors.caretHovered,
        placeholder = retrieveColorOrUnspecified("Label.infoForeground"),
        label = retrieveColorOrUnspecified("Label.foreground"),
        hint = StatusText.DEFAULT_ATTRIBUTES.fgColor.toComposeColor(),
    )

    return IntUiLabelledTextFieldStyle(
        colors = colors,
        metrics = IntUiLabelledTextFieldMetrics(
            cornerSize = inputFieldStyle.metrics.cornerSize,
            contentPadding = inputFieldStyle.metrics.contentPadding,
            minSize = inputFieldStyle.metrics.minSize,
            borderWidth = inputFieldStyle.metrics.borderWidth,
            labelSpacing = 6.dp,
            hintSpacing = 6.dp,
        ),
        textStyle = inputFieldStyle.textStyle,
        textStyles = IntUiLabelledTextFieldTextStyles(
            label = labelTextStyle,
            hint = labelTextStyle.copy(fontSize = labelTextStyle.fontSize - 1f),
        ),
    )
}

private fun readLinkStyle(
    linkTextStyle: TextStyle,
): IntUiLinkStyle {
    val normalContent =
        retrieveColorOrUnspecified("Link.activeForeground").takeOrElse { retrieveColorOrUnspecified("Link.activeForeground") }

    val colors = IntUiLinkColors(
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("Link.disabledForeground").takeOrElse {
            retrieveColorOrUnspecified(
                "Label.disabledForeground",
            )
        },
        contentFocused = normalContent,
        contentPressed = retrieveColorOrUnspecified("Link.pressedForeground").takeOrElse { retrieveColorOrUnspecified("link.pressed.foreground") },
        contentHovered = retrieveColorOrUnspecified("Link.hoverForeground").takeOrElse { retrieveColorOrUnspecified("link.hover.foreground") },
        contentVisited = retrieveColorOrUnspecified("Link.visitedForeground").takeOrElse { retrieveColorOrUnspecified("link.visited.foreground") },
    )

    return IntUiLinkStyle(
        colors = colors,
        metrics = IntUiLinkMetrics(
            focusHaloCornerSize = CornerSize(Registry.intValue("ide.link.button.focus.round.arc", 4).dp),
            textIconGap = 4.dp,
            iconSize = DpSize.Unspecified,
        ),
        icons = IntUiLinkIcons(
            dropdownChevron = bridgePainterProvider("${iconsBasePath}general/chevron-down.svg"),
            externalLink = bridgePainterProvider("${iconsBasePath}ide/external_link_arrow.svg"),
        ),
        textStyles = IntUiLinkTextStyles(
            normal = linkTextStyle,
            disabled = linkTextStyle,
            focused = linkTextStyle,
            pressed = linkTextStyle,
            hovered = linkTextStyle,
            visited = linkTextStyle,
        ),
    )
}

private fun readMenuStyle(): IntUiMenuStyle {
    val backgroundSelected = retrieveColorOrUnspecified("MenuItem.selectionBackground")
    val foregroundSelected = retrieveColorOrUnspecified("MenuItem.selectionForeground")

    val colors = IntUiMenuColors(
        background = retrieveColorOrUnspecified("PopupMenu.background"),
        border = retrieveColorOrUnspecified("Popup.borderColor").takeOrElse { retrieveColorOrUnspecified("Popup.Border.color") },
        shadow = Color.Black.copy(alpha = .6f),
        itemColors = IntUiMenuItemColors(
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
            separator = retrieveColorOrUnspecified("Menu.separatorColor"),
        ),
    )

    return IntUiMenuStyle(
        colors = colors,
        metrics = IntUiMenuMetrics(
            cornerSize = CornerSize(IdeaPopupMenuUI.CORNER_RADIUS.dp),
            menuMargin = PaddingValues(0.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            offset = DpOffset(0.dp, 2.dp),
            shadowSize = 12.dp,
            borderWidth = retrieveIntAsDpOrUnspecified("Popup.borderWidth").takeOrElse { 1.dp },
            itemMetrics = IntUiMenuItemMetrics(
                selectionCornerSize = CornerSize(JBUI.CurrentTheme.PopupMenu.Selection.ARC.dp),
                outerPadding = PaddingValues(horizontal = 6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 1.dp),
                separatorPadding = PaddingValues(
                    horizontal = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.withToEdge").takeOrElse { 0.dp },
                    vertical = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeIndent").takeOrElse { 0.dp },
                ),
                separatorThickness = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeWidth").takeOrElse { 0.dp },
            ),
            submenuMetrics = IntUiSubmenuMetrics(
                offset = DpOffset(0.dp, (-8).dp),
            ),
        ),
        icons = IntUiMenuIcons(
            submenuChevron = bridgePainterProvider("${iconsBasePath}general/chevron-down.svg"),
        ),
    )
}

private fun readRadioButtonStyle(): IntUiRadioButtonStyle {
    val normalContent = retrieveColorOrUnspecified("RadioButton.foreground")
    val disabledContent = retrieveColorOrUnspecified("RadioButton.disabledText")
    val colors = IntUiRadioButtonColors(
        content = normalContent,
        contentHovered = normalContent,
        contentDisabled = disabledContent,
        contentSelected = normalContent,
        contentSelectedHovered = normalContent,
        contentSelectedDisabled = disabledContent,
    )

    return IntUiRadioButtonStyle(
        colors = colors,
        metrics = IntUiRadioButtonMetrics(
            radioButtonSize = DpSize(19.dp, 19.dp),
            iconContentGap = retrieveIntAsDpOrUnspecified("RadioButton.textIconGap").takeOrElse { 4.dp },
        ),
        icons = IntUiRadioButtonIcons(
            radioButton = bridgePainterProvider("${iconsBasePath}radio.svg"),
        ),
    )
}

private fun readScrollbarStyle(isDark: Boolean) = IntUiScrollbarStyle(
    colors = IntUiScrollbarColors(
        // See ScrollBarPainter.THUMB_OPAQUE_BACKGROUND
        thumbBackground = retrieveColorOrUnspecified("ScrollBar.Mac.Transparent.thumbColor")
            .takeOrElse { if (isDark) Color(0x59808080) else Color(0x33000000) },
        // See ScrollBarPainter.THUMB_OPAQUE_HOVERED_BACKGROUND
        thumbBackgroundHovered = retrieveColorOrUnspecified("ScrollBar.Mac.Transparent.hoverThumbColor")
            .takeOrElse { if (isDark) Color(0x8C808080) else Color(0x80000000) },
    ),
    metrics = IntUiScrollbarMetrics(
        thumbCornerSize = CornerSize(100),
        thumbThickness = 8.dp,
        minThumbLength = 16.dp,
        trackPadding = PaddingValues(start = 7.dp, end = 3.dp),
    ),
    hoverDuration = 300.milliseconds,
)

private fun readTextAreaStyle(textStyle: TextStyle, metrics: IntUiTextFieldMetrics): IntUiTextAreaStyle {
    val normalBackground = retrieveColorOrUnspecified("TextArea.background")
    val normalContent = retrieveColorOrUnspecified("TextArea.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextArea.caretForeground")

    val colors = IntUiTextAreaColors(
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

    return IntUiTextAreaStyle(
        colors = colors,
        metrics = IntUiTextAreaMetrics(
            cornerSize = metrics.cornerSize,
            contentPadding = metrics.contentPadding,
            minSize = metrics.minSize,
            borderWidth = metrics.borderWidth,
        ),
        textStyle = textStyle,
    )
}

private fun readTextFieldStyle(textFieldStyle: TextStyle): IntUiTextFieldStyle {
    val normalBackground = retrieveColorOrUnspecified("TextField.background")
    val normalContent = retrieveColorOrUnspecified("TextField.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextField.caretForeground")

    val colors = IntUiTextFieldColors(
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

    return IntUiTextFieldStyle(
        colors = colors,
        metrics = IntUiTextFieldMetrics(
            cornerSize = CornerSize(DarculaUIUtil.COMPONENT_ARC.dp),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
            minSize = DpSize(DarculaUIUtil.MINIMUM_WIDTH.dp, DarculaUIUtil.MINIMUM_HEIGHT.dp),
            borderWidth = DarculaUIUtil.LW.dp,
        ),
        textStyle = textFieldStyle,
    )
}

private fun readLazyTreeStyle(): IntUiLazyTreeStyle {
    val normalContent = retrieveColorOrUnspecified("Tree.foreground")
    val selectedContent = retrieveColorOrUnspecified("Tree.selectionForeground")
    val selectedElementBackground = retrieveColorOrUnspecified("Tree.selectionBackground")
    val inactiveSelectedElementBackground = retrieveColorOrUnspecified("Tree.selectionInactiveBackground")

    val colors = IntUiLazyTreeColors(
        content = normalContent,
        contentFocused = normalContent,
        contentSelected = selectedContent,
        contentSelectedFocused = selectedContent,
        elementBackgroundFocused = Color.Transparent,
        elementBackgroundSelected = inactiveSelectedElementBackground,
        elementBackgroundSelectedFocused = selectedElementBackground,
    )

    val chevronCollapsed = bridgePainterProvider("${iconsBasePath}general/chevron-right.svg")
    val chevronExpanded = bridgePainterProvider("${iconsBasePath}general/chevron-down.svg")

    return IntUiLazyTreeStyle(
        colors = colors,
        metrics = IntUiLazyTreeMetrics(
            indentSize = retrieveIntAsDpOrUnspecified("Tree.leftChildIndent").takeOrElse { 7.dp } +
                retrieveIntAsDpOrUnspecified("Tree.rightChildIndent").takeOrElse { 11.dp },
            elementBackgroundCornerSize = CornerSize(JBUI.CurrentTheme.Tree.ARC.dp / 2),
            elementPadding = PaddingValues(horizontal = 12.dp),
            elementContentPadding = PaddingValues(4.dp),
            elementMinHeight = retrieveIntAsDpOrUnspecified("Tree.rowHeight").takeOrElse { 24.dp },
            chevronContentGap = 2.dp, // See com.intellij.ui.tree.ui.ClassicPainter.GAP
        ),
        icons = IntUiLazyTreeIcons(
            chevronCollapsed = chevronCollapsed,
            chevronExpanded = chevronExpanded,
            chevronSelectedCollapsed = chevronCollapsed,
            chevronSelectedExpanded = chevronExpanded,
        ),
    )
}

// See com.intellij.ui.tabs.impl.themes.DefaultTabTheme
private fun readDefaultTabStyle(): IntUiTabStyle {
    val normalBackground = JBUI.CurrentTheme.DefaultTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.DefaultTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors = IntUiTabColors(
        background = normalBackground,
        backgroundDisabled = normalBackground,
        backgroundFocused = normalBackground,
        backgroundPressed = selectedBackground,
        backgroundHovered = JBUI.CurrentTheme.DefaultTabs.hoverBackground().toComposeColor(),
        backgroundSelected = selectedBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("TabbedPane.disabledForeground"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        contentSelected = normalContent,
        underline = Color.Transparent,
        underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
        underlineFocused = Color.Transparent,
        underlinePressed = selectedUnderline,
        underlineHovered = Color.Transparent,
        underlineSelected = selectedUnderline,
    )

    return IntUiTabStyle(
        colors = colors,
        metrics = IntUiTabMetrics(
            underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight").takeOrElse { 2.dp },
            tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
            closeContentGap = 4.dp,
            tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight").takeOrElse { 24.dp },
        ),
        icons = IntUiTabIcons(
            close = bridgePainterProvider("${iconsBasePath}expui/general/closeSmall.svg"),
        ),
        contentAlpha = IntUiTabContentAlpha(
            iconNormal = 1f,
            iconDisabled = 1f,
            iconFocused = 1f,
            iconPressed = 1f,
            iconHovered = 1f,
            iconSelected = 1f,
            labelNormal = 1f,
            labelDisabled = 1f,
            labelFocused = 1f,
            labelPressed = 1f,
            labelHovered = 1f,
            labelSelected = 1f,
        ),
    )
}

private fun readEditorTabStyle(): IntUiTabStyle {
    val normalBackground = JBUI.CurrentTheme.EditorTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.EditorTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors = IntUiTabColors(
        background = normalBackground,
        backgroundDisabled = normalBackground,
        backgroundFocused = normalBackground,
        backgroundPressed = selectedBackground,
        backgroundHovered = JBUI.CurrentTheme.EditorTabs.hoverBackground().toComposeColor(),
        backgroundSelected = selectedBackground,
        content = normalContent,
        contentDisabled = retrieveColorOrUnspecified("TabbedPane.disabledForeground"),
        contentFocused = normalContent,
        contentPressed = normalContent,
        contentHovered = normalContent,
        contentSelected = normalContent,
        underline = Color.Transparent,
        underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
        underlineFocused = Color.Transparent,
        underlinePressed = selectedUnderline,
        underlineHovered = Color.Transparent,
        underlineSelected = selectedUnderline,
    )

    return IntUiTabStyle(
        colors = colors,
        metrics = IntUiTabMetrics(
            underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight").takeOrElse { 2.dp },
            tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
            closeContentGap = 4.dp,
            tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight").takeOrElse { 24.dp },
        ),
        icons = IntUiTabIcons(
            close = bridgePainterProvider("${iconsBasePath}expui/general/closeSmall.svg"),
        ),
        contentAlpha = IntUiTabContentAlpha(
            iconNormal = .7f,
            iconDisabled = .7f,
            iconFocused = .7f,
            iconPressed = 1f,
            iconHovered = 1f,
            iconSelected = 1f,
            labelNormal = .7f,
            labelDisabled = .7f,
            labelFocused = .7f,
            labelPressed = 1f,
            labelHovered = 1f,
            labelSelected = 1f,
        ),
    )
}

private fun readCircularProgressStyle(
    isDark: Boolean,
): IntUiCircularProgressStyle =
    IntUiCircularProgressStyle(
        frameTime = 125.milliseconds,
        color = retrieveColorOrUnspecified("ProgressIcon.color")
            .takeIf { it.isSpecified }
            ?: if (isDark) Color(0xFF6F737A) else Color(0xFFA8ADBD),
    )

private fun readTooltipStyle(): IntUiTooltipStyle {
    return IntUiTooltipStyle(
        metrics = IntUiTooltipMetrics(),
        colors = IntUiTooltipColors(
            content = retrieveColorOrUnspecified("ToolTip.foreground"),
            background = retrieveColorOrUnspecified("ToolTip.background"),
            border = retrieveColorOrUnspecified("ToolTip.borderColor"),
            shadow = retrieveColorOrUnspecified("Notification.Shadow.bottom1Color"),
        ),
    )
}

private fun readIconButtonStyle(): IntUiIconButtonStyle = IntUiIconButtonStyle(
    metrics = IntUiIconButtonMetrics(CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2)),
    colors = IntUiIconButtonColors(
        background = Color.Unspecified,
        backgroundDisabled = Color.Unspecified,
        backgroundFocused = Color.Unspecified,
        backgroundPressed = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
        backgroundHovered = retrieveColorOrUnspecified("ActionButton.hoverBackground"),
        border = Color.Unspecified,
        borderDisabled = Color.Unspecified,
        borderFocused = retrieveColorOrUnspecified("ActionButton.focusedBorderColor"),
        borderPressed = retrieveColorOrUnspecified("ActionButton.pressedBorderColor"),
        borderHovered = retrieveColorOrUnspecified("ActionButton.hoverBorderColor"),
    ),
)
