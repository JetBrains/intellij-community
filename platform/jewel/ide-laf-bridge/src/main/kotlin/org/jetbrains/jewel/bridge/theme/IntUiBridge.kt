package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JBColor
import com.intellij.util.ui.DirProvider
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import javax.swing.UIManager
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.isNewUiTheme
import org.jetbrains.jewel.bridge.lafName
import org.jetbrains.jewel.bridge.readFromLaF
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeOrDefault
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeWithFallbacks
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveColorsOrUnspecified
import org.jetbrains.jewel.bridge.retrieveEditorColorScheme
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValues
import org.jetbrains.jewel.bridge.retrieveIntAsDpOrUnspecified
import org.jetbrains.jewel.bridge.retrieveTextStyle
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified
import org.jetbrains.jewel.bridge.toDpSize
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.foundation.util.JewelLogger
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
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior
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
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonColors
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonMetrics
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlColors
import org.jetbrains.jewel.ui.component.styling.SegmentedControlMetrics
import org.jetbrains.jewel.ui.component.styling.SegmentedControlStyle
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
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private val logger = JewelLogger.getInstance("JewelIntUiBridge")

internal val uiDefaults
    get() = UIManager.getDefaults()

private val iconsBasePath
    get() = DirProvider().dir()

internal fun createBridgeThemeDefinition(): ThemeDefinition {
    val textStyle = retrieveDefaultTextStyle()
    val editorTextStyle = retrieveEditorTextStyle()
    val consoleTextStyle = retrieveConsoleTextStyle()
    return createBridgeThemeDefinition(textStyle, editorTextStyle, consoleTextStyle)
}

public fun retrieveDefaultTextStyle(): TextStyle = retrieveTextStyle("Label.font", "Label.foreground")

@OptIn(ExperimentalTextApi::class)
public fun retrieveEditorTextStyle(): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()

    val fontSize = editorColorScheme.editorFontSize.sp
    return retrieveDefaultTextStyle()
        .copy(
            color = editorColorScheme.defaultForeground.toComposeColor(),
            fontFamily = editorColorScheme.getFont(EditorFontType.PLAIN).asComposeFontFamily(),
            fontSize = fontSize,
            lineHeight = fontSize * editorColorScheme.lineSpacing,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0" else "liga 1",
        )
}

@OptIn(ExperimentalTextApi::class)
public fun retrieveConsoleTextStyle(): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()
    if (editorColorScheme.isUseEditorFontPreferencesInConsole) return retrieveEditorTextStyle()

    val fontSize = editorColorScheme.consoleFontSize.sp
    val fontColor =
        editorColorScheme.getColor(ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_FOREGROUND"))
            ?: editorColorScheme.defaultForeground

    return retrieveDefaultTextStyle()
        .copy(
            color = fontColor.toComposeColor(),
            fontFamily = editorColorScheme.getFont(EditorFontType.CONSOLE_PLAIN).asComposeFontFamily(),
            fontSize = fontSize,
            lineHeight = fontSize * editorColorScheme.lineSpacing,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0" else "liga 1",
        )
}

private val isDark: Boolean
    get() = !JBColor.isBright()

internal fun createBridgeThemeDefinition(
    textStyle: TextStyle,
    editorTextStyle: TextStyle,
    consoleTextStyle: TextStyle,
): ThemeDefinition {
    logger.debug("Obtaining theme definition from Swing...")

    return ThemeDefinition(
        name = lafName(),
        isDark = isDark,
        globalColors = GlobalColors.readFromLaF(),
        globalMetrics = GlobalMetrics.readFromLaF(),
        defaultTextStyle = textStyle,
        editorTextStyle = editorTextStyle,
        consoleTextStyle = consoleTextStyle,
        contentColor = JBColor.foreground().toComposeColor(),
        colorPalette = ThemeColorPalette.readFromLaF(),
        iconData = ThemeIconData.readFromLaF(),
    )
}

internal fun createBridgeComponentStyling(theme: ThemeDefinition): ComponentStyling {
    logger.debug("Obtaining Int UI component styling from Swing...")

    val textFieldStyle = readTextFieldStyle()
    val menuStyle = readMenuStyle()

    return DefaultComponentStyling(
        checkboxStyle = readCheckboxStyle(),
        chipStyle = readChipStyle(),
        circularProgressStyle = readCircularProgressStyle(theme.isDark),
        defaultButtonStyle = readDefaultButtonStyle(),
        defaultDropdownStyle = readDefaultDropdownStyle(menuStyle),
        defaultTabStyle = readDefaultTabStyle(),
        dividerStyle = readDividerStyle(),
        editorTabStyle = readEditorTabStyle(),
        groupHeaderStyle = readGroupHeaderStyle(),
        horizontalProgressBarStyle = readHorizontalProgressBarStyle(),
        iconButtonStyle = readIconButtonStyle(),
        lazyTreeStyle = readLazyTreeStyle(),
        linkStyle = readLinkStyle(),
        menuStyle = menuStyle,
        outlinedButtonStyle = readOutlinedButtonStyle(),
        radioButtonStyle = readRadioButtonStyle(),
        scrollbarStyle = readScrollbarStyle(theme.isDark),
        segmentedControlButtonStyle = readSegmentedControlButtonStyle(),
        segmentedControlStyle = readSegmentedControlStyle(),
        sliderStyle = readSliderStyle(theme.isDark),
        textAreaStyle = readTextAreaStyle(textFieldStyle.metrics),
        textFieldStyle = textFieldStyle,
        tooltipStyle = readTooltipStyle(),
        undecoratedDropdownStyle = readUndecoratedDropdownStyle(menuStyle),
    )
}

private fun readDefaultButtonStyle(): ButtonStyle {
    val normalBackground =
        listOf(
                JBUI.CurrentTheme.Button.defaultButtonColorStart().toComposeColor(),
                JBUI.CurrentTheme.Button.defaultButtonColorEnd().toComposeColor(),
            )
            .createVerticalBrush()

    val normalContent = retrieveColorOrUnspecified("Button.default.foreground")

    val normalBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(true).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(true).toComposeColor(),
            )
            .createVerticalBrush()

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

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toDpSize()
    return ButtonStyle(
        colors = colors,
        metrics =
            ButtonMetrics(
                cornerSize = retrieveArcAsCornerSizeWithFallbacks("Button.default.arc", "Button.arc"),
                padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = 1.dp,
                focusOutlineExpand = 1.5.dp, // From DarculaButtonPainter.getBorderInsets
            ),
        focusOutlineAlignment = Stroke.Alignment.Center,
    )
}

private fun readOutlinedButtonStyle(): ButtonStyle {
    val normalBackground =
        listOf(
                JBUI.CurrentTheme.Button.buttonColorStart().toComposeColor(),
                JBUI.CurrentTheme.Button.buttonColorEnd().toComposeColor(),
            )
            .createVerticalBrush()

    val normalContent = retrieveColorOrUnspecified("Button.foreground")

    val normalBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false).toComposeColor(),
            )
            .createVerticalBrush()

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
            borderFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
            borderPressed = normalBorder,
            borderHovered = normalBorder,
        )

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toDpSize()
    return ButtonStyle(
        colors = colors,
        metrics =
            ButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                padding = PaddingValues(horizontal = 14.dp), // see DarculaButtonUI.HORIZONTAL_PADDING
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = DarculaUIUtil.LW.dp,
                focusOutlineExpand = Dp.Unspecified,
            ),
        focusOutlineAlignment = Stroke.Alignment.Center,
    )
}

private fun readCheckboxStyle(): CheckboxStyle {
    val textColor = retrieveColorOrUnspecified("CheckBox.foreground")
    val colors =
        CheckboxColors(
            content = textColor,
            contentDisabled = retrieveColorOrUnspecified("CheckBox.disabledText"),
            contentSelected = textColor,
        )

    val newUiTheme = isNewUiTheme()
    val metrics = if (newUiTheme) NewUiCheckboxMetrics else ClassicUiCheckboxMetrics

    // This value is not normally defined in the themes, but Swing checks it anyway.
    // The default hardcoded in
    // com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI.getDefaultIcon()
    // is not correct though, the SVG is 19x19 and is missing 1px on the right
    val checkboxSize =
        retrieveIntAsDpOrUnspecified("CheckBox.iconSize").let {
            when {
                it.isSpecified -> DpSize(it, it)
                else -> metrics.checkboxSize
            }
        }

    return CheckboxStyle(
        colors = colors,
        metrics =
            CheckboxMetrics(
                checkboxSize = checkboxSize,
                outlineCornerSize = CornerSize(metrics.outlineCornerSize),
                outlineFocusedCornerSize = CornerSize(metrics.outlineFocusedCornerSize),
                outlineSelectedCornerSize = CornerSize(metrics.outlineSelectedCornerSize),
                outlineSelectedFocusedCornerSize = CornerSize(metrics.outlineSelectedFocusedCornerSize),
                outlineSize = metrics.outlineSize,
                outlineSelectedSize = metrics.outlineSelectedSize,
                outlineFocusedSize = metrics.outlineFocusedSize,
                outlineSelectedFocusedSize = metrics.outlineSelectedFocusedSize,
                iconContentGap = metrics.iconContentGap,
            ),
        icons = CheckboxIcons(checkbox = PathIconKey("${iconsBasePath}checkBox.svg", CheckboxIcons::class.java)),
    )
}

private interface BridgeCheckboxMetrics {
    val outlineSize: DpSize
    val outlineFocusedSize: DpSize
    val outlineSelectedSize: DpSize
    val outlineSelectedFocusedSize: DpSize

    val outlineCornerSize: Dp
    val outlineFocusedCornerSize: Dp
    val outlineSelectedCornerSize: Dp
    val outlineSelectedFocusedCornerSize: Dp

    val checkboxSize: DpSize
    val iconContentGap: Dp
}

private object ClassicUiCheckboxMetrics : BridgeCheckboxMetrics {
    override val outlineSize = DpSize(14.dp, 14.dp)
    override val outlineFocusedSize = DpSize(15.dp, 15.dp)
    override val outlineSelectedSize = outlineSize
    override val outlineSelectedFocusedSize = outlineFocusedSize

    override val outlineCornerSize = 2.dp
    override val outlineFocusedCornerSize = 3.dp
    override val outlineSelectedCornerSize = outlineCornerSize
    override val outlineSelectedFocusedCornerSize = outlineFocusedCornerSize

    override val checkboxSize = DpSize(20.dp, 19.dp)
    override val iconContentGap = 4.dp
}

private object NewUiCheckboxMetrics : BridgeCheckboxMetrics {
    override val outlineSize = DpSize(16.dp, 16.dp)
    override val outlineFocusedSize = outlineSize
    override val outlineSelectedSize = DpSize(20.dp, 20.dp)
    override val outlineSelectedFocusedSize = outlineSelectedSize

    override val outlineCornerSize = 3.dp
    override val outlineFocusedCornerSize = outlineCornerSize
    override val outlineSelectedCornerSize = 4.5.dp
    override val outlineSelectedFocusedCornerSize = outlineSelectedCornerSize

    override val checkboxSize = DpSize(24.dp, 24.dp)
    override val iconContentGap = 5.dp
}

// Note: there isn't a chip spec, nor a chip UI, so we're deriving this from the
// styling defined in com.intellij.ide.ui.experimental.meetNewUi.MeetNewUiButton
// To note:
//  1. There is no real disabled state, we're making it sort of up
//  2. Chips can be used as buttons (see run configs) or as radio buttons (see MeetNewUi)
//  3. We also have a toggleable version because why not
private fun readChipStyle(): ChipStyle {
    val normalBackground =
        retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground").createVerticalBrush()
    val normalContent = retrieveColorOrUnspecified("Label.foreground")
    val normalBorder = retrieveColorOrUnspecified("Button.startBorderColor")
    val disabledBorder = retrieveColorOrUnspecified("Button.disabledBorderColor")
    val selectedBorder = retrieveColorOrUnspecified("Component.focusColor")

    val colors =
        ChipColors(
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
        metrics =
            ChipMetrics(
                cornerSize = CornerSize(6.dp),
                padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                borderWidth = 1.dp,
                borderWidthSelected = 2.dp,
            ),
    )
}

private fun readDividerStyle() =
    DividerStyle(color = JBColor.border().toComposeColorOrUnspecified(), metrics = DividerMetrics.defaults())

private fun readDefaultDropdownStyle(menuStyle: MenuStyle): DropdownStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.nonEditableBackground")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")
    val normalBorder = retrieveColorOrUnspecified("Component.borderColor")
    val focusedBorder = retrieveColorOrUnspecified("Component.focusedBorderColor")

    val colors =
        DropdownColors(
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

    val minimumSize = JBUI.CurrentTheme.ComboBox.minimumSize().toDpSize()
    val arrowWidth = JBUI.CurrentTheme.Component.ARROW_AREA_WIDTH.dp
    return DropdownStyle(
        colors = colors,
        metrics =
            DropdownMetrics(
                arrowMinSize = DpSize(arrowWidth, minimumSize.height),
                minSize = DpSize(minimumSize.width + arrowWidth, minimumSize.height),
                cornerSize = componentArc,
                contentPadding = retrieveInsetsAsPaddingValues("ComboBox.padding"),
                borderWidth = DarculaUIUtil.LW.dp,
            ),
        icons = DropdownIcons(chevronDown = AllIconsKeys.General.ChevronDown),
        menuStyle = menuStyle,
    )
}

private fun readUndecoratedDropdownStyle(menuStyle: MenuStyle): DropdownStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.nonEditableBackground")
    val hoverBackground = retrieveColorOrUnspecified("MainToolbar.Dropdown.transparentHoverBackground")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")

    val colors =
        DropdownColors(
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
            border = Color.Unspecified,
            borderDisabled = Color.Unspecified,
            borderFocused = Color.Unspecified,
            borderPressed = Color.Unspecified,
            borderHovered = Color.Unspecified,
            iconTint = Color.Unspecified,
            iconTintDisabled = Color.Unspecified,
            iconTintFocused = Color.Unspecified,
            iconTintPressed = Color.Unspecified,
            iconTintHovered = Color.Unspecified,
        )

    val arrowWidth = JBUI.CurrentTheme.Component.ARROW_AREA_WIDTH.dp
    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toDpSize()

    return DropdownStyle(
        colors = colors,
        metrics =
            DropdownMetrics(
                arrowMinSize = DpSize(arrowWidth, minimumSize.height),
                minSize = DpSize(minimumSize.width + arrowWidth, minimumSize.height),
                cornerSize = CornerSize(JBUI.CurrentTheme.MainToolbar.Dropdown.hoverArc().dp),
                contentPadding = PaddingValues(3.dp), // from
                // com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI.getDefaultComboBoxInsets
                borderWidth = 0.dp,
            ),
        icons = DropdownIcons(chevronDown = AllIconsKeys.General.ChevronDown),
        menuStyle = menuStyle,
    )
}

private fun readGroupHeaderStyle() =
    GroupHeaderStyle(
        colors = GroupHeaderColors(divider = retrieveColorOrUnspecified("Separator.separatorColor")),
        metrics =
            GroupHeaderMetrics(
                dividerThickness = 1.dp, // see DarculaSeparatorUI
                indent = 1.dp, // see DarculaSeparatorUI
            ),
    )

private fun readHorizontalProgressBarStyle() =
    HorizontalProgressBarStyle(
        colors =
            HorizontalProgressBarColors(
                track = retrieveColorOrUnspecified("ProgressBar.trackColor"),
                progress = retrieveColorOrUnspecified("ProgressBar.progressColor"),
                indeterminateBase = retrieveColorOrUnspecified("ProgressBar.indeterminateStartColor"),
                indeterminateHighlight = retrieveColorOrUnspecified("ProgressBar.indeterminateEndColor"),
            ),
        metrics =
            HorizontalProgressBarMetrics(
                cornerSize = CornerSize(100),
                minHeight = 4.dp, // See DarculaProgressBarUI.DEFAULT_WIDTH
                // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT,
                // DarculaProgressBarUI.REPAINT_INTERVAL_DEFAULT,
                // and the "step" constant in DarculaProgressBarUI#paintIndeterminate
                indeterminateHighlightWidth = (800 / 50 * 6).dp,
            ),
        indeterminateCycleDuration = 800.milliseconds, // See DarculaProgressBarUI.CYCLE_TIME_DEFAULT
    )

private fun readLinkStyle(): LinkStyle {
    val normalContent =
        retrieveColorOrUnspecified("Link.activeForeground").takeOrElse {
            retrieveColorOrUnspecified("Link.activeForeground")
        }

    val colors =
        LinkColors(
            content = normalContent,
            contentDisabled =
                retrieveColorOrUnspecified("Link.disabledForeground").takeOrElse {
                    retrieveColorOrUnspecified("Label.disabledForeground")
                },
            contentFocused = normalContent,
            contentPressed =
                retrieveColorOrUnspecified("Link.pressedForeground").takeOrElse {
                    retrieveColorOrUnspecified("link.pressed.foreground")
                },
            contentHovered =
                retrieveColorOrUnspecified("Link.hoverForeground").takeOrElse {
                    retrieveColorOrUnspecified("link.hover.foreground")
                },
            contentVisited =
                retrieveColorOrUnspecified("Link.visitedForeground").takeOrElse {
                    retrieveColorOrUnspecified("link.visited.foreground")
                },
        )

    return LinkStyle(
        colors = colors,
        metrics =
            LinkMetrics(
                focusHaloCornerSize =
                    retrieveArcAsCornerSizeOrDefault(
                        key = "ide.link.button.focus.round.arc",
                        default = CornerSize(4.dp),
                    ),
                textIconGap = 4.dp,
                iconSize = DpSize(16.dp, 16.dp),
            ),
        icons =
            LinkIcons(
                dropdownChevron = AllIconsKeys.General.ChevronDown,
                externalLink = AllIconsKeys.Ide.External_link_arrow,
            ),
        underlineBehavior = LinkUnderlineBehavior.ShowOnHover,
    )
}

private fun readMenuStyle(): MenuStyle {
    val backgroundSelected = retrieveColorOrUnspecified("MenuItem.selectionBackground")
    val foregroundSelected = retrieveColorOrUnspecified("MenuItem.selectionForeground")
    val keybindingTint = retrieveColorOrUnspecified("MenuItem.acceleratorForeground")
    val keybindingTintSelected = Color.Unspecified

    val colors =
        MenuColors(
            background = retrieveColorOrUnspecified("PopupMenu.background"),
            border =
                retrieveColorOrUnspecified("Popup.borderColor").takeOrElse {
                    retrieveColorOrUnspecified("Popup.Border.color")
                },
            shadow = Color.Black.copy(alpha = .6f),
            itemColors =
                MenuItemColors(
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
        isDark = isDark,
        colors = colors,
        metrics =
            MenuMetrics(
                cornerSize = CornerSize(IdeaPopupMenuUI.CORNER_RADIUS.dp),
                menuMargin = PaddingValues(0.dp),
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
                offset = DpOffset(0.dp, 2.dp),
                shadowSize = 12.dp,
                borderWidth = retrieveIntAsDpOrUnspecified("Popup.borderWidth").takeOrElse { 1.dp },
                itemMetrics =
                    MenuItemMetrics(
                        selectionCornerSize = CornerSize(JBUI.CurrentTheme.PopupMenu.Selection.ARC.dp / 2),
                        outerPadding = PaddingValues(horizontal = 7.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        keybindingsPadding = PaddingValues(start = 36.dp),
                        separatorPadding =
                            PaddingValues(
                                horizontal =
                                    retrieveIntAsDpOrUnspecified("PopupMenuSeparator.withToEdge").takeOrElse { 1.dp },
                                vertical =
                                    retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeIndent").takeOrElse { 1.dp },
                            ),
                        separatorThickness =
                            retrieveIntAsDpOrUnspecified("PopupMenuSeparator.stripeWidth").takeOrElse { 1.dp },
                        separatorHeight = retrieveIntAsDpOrUnspecified("PopupMenuSeparator.height").takeOrElse { 3.dp },
                        iconSize = 16.dp,
                        minHeight = if (isNewUiTheme()) JBUI.CurrentTheme.List.rowHeight().dp else Dp.Unspecified,
                    ),
                submenuMetrics = SubmenuMetrics(offset = DpOffset(0.dp, (-8).dp)),
            ),
        icons = MenuIcons(submenuChevron = AllIconsKeys.General.ChevronRight),
    )
}

private fun readRadioButtonStyle(): RadioButtonStyle {
    val normalContent = retrieveColorOrUnspecified("RadioButton.foreground")
    val disabledContent = retrieveColorOrUnspecified("RadioButton.disabledText")
    val colors =
        RadioButtonColors(
            content = normalContent,
            contentHovered = normalContent,
            contentDisabled = disabledContent,
            contentSelected = normalContent,
            contentSelectedHovered = normalContent,
            contentSelectedDisabled = disabledContent,
        )

    val newUiTheme = isNewUiTheme()
    val metrics = if (newUiTheme) NewUiRadioButtonMetrics else ClassicUiRadioButtonMetrics

    // This value is not normally defined in the themes, but Swing checks it anyway
    // The default hardcoded in
    // com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI.getDefaultIcon()
    // is not correct though, the SVG is 19x19 and is missing 1px on the right
    val radioButtonSize =
        retrieveIntAsDpOrUnspecified("RadioButton.iconSize")
            .takeOrElse { metrics.radioButtonSize }
            .let { DpSize(it, it) }

    // val outlineSize = if (isNewUiButNotDarcula() DpSize(17.dp, 17.dp) else

    return RadioButtonStyle(
        colors = colors,
        metrics =
            RadioButtonMetrics(
                radioButtonSize = radioButtonSize,
                outlineSize = metrics.outlineSize,
                outlineFocusedSize = metrics.outlineFocusedSize,
                outlineSelectedSize = metrics.outlineSelectedSize,
                outlineSelectedFocusedSize = metrics.outlineSelectedFocusedSize,
                iconContentGap =
                    retrieveIntAsDpOrUnspecified("RadioButton.textIconGap").takeOrElse { metrics.iconContentGap },
            ),
        icons = RadioButtonIcons(radioButton = PathIconKey("${iconsBasePath}radio.svg", RadioButtonIcons::class.java)),
    )
}

private interface BridgeRadioButtonMetrics {
    val outlineSize: DpSize
    val outlineFocusedSize: DpSize
    val outlineSelectedSize: DpSize
    val outlineSelectedFocusedSize: DpSize

    val radioButtonSize: Dp
    val iconContentGap: Dp
}

private object ClassicUiRadioButtonMetrics : BridgeRadioButtonMetrics {
    override val outlineSize = DpSize(17.dp, 17.dp)
    override val outlineFocusedSize = DpSize(19.dp, 19.dp)
    override val outlineSelectedSize = outlineSize
    override val outlineSelectedFocusedSize = outlineFocusedSize

    override val radioButtonSize = 19.dp
    override val iconContentGap = 4.dp
}

private object NewUiRadioButtonMetrics : BridgeRadioButtonMetrics {
    override val outlineSize = DpSize(17.dp, 17.dp)
    override val outlineFocusedSize = outlineSize
    override val outlineSelectedSize = DpSize(22.dp, 22.dp)
    override val outlineSelectedFocusedSize = outlineSelectedSize

    override val radioButtonSize = 24.dp
    override val iconContentGap = 4.dp
}

private fun readSegmentedControlButtonStyle(): SegmentedControlButtonStyle {
    val selectedBackground = SolidColor(JBUI.CurrentTheme.SegmentedButton.SELECTED_BUTTON_COLOR.toComposeColor())

    val normalBorder =
        listOf(
                JBUI.CurrentTheme.SegmentedButton.SELECTED_START_BORDER_COLOR.toComposeColor(),
                JBUI.CurrentTheme.SegmentedButton.SELECTED_END_BORDER_COLOR.toComposeColor(),
            )
            .createVerticalBrush()

    val selectedDisabledBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false).toComposeColor(),
            )
            .createVerticalBrush()

    val colors =
        SegmentedControlButtonColors(
            background = SolidColor(Color.Transparent),
            backgroundPressed = selectedBackground,
            backgroundHovered = SolidColor(JBUI.CurrentTheme.ActionButton.hoverBackground().toComposeColor()),
            backgroundSelected = selectedBackground,
            backgroundSelectedFocused =
                SolidColor(JBUI.CurrentTheme.SegmentedButton.FOCUSED_SELECTED_BUTTON_COLOR.toComposeColor()),
            content = retrieveColorOrUnspecified("Button.foreground"),
            contentDisabled = retrieveColorOrUnspecified("Label.disabledForeground"),
            border = normalBorder,
            borderSelected = normalBorder,
            borderSelectedDisabled = selectedDisabledBorder,
            borderSelectedFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
        )

    val minimumSize = JBUI.CurrentTheme.Button.minimumSize().toDpSize()
    return SegmentedControlButtonStyle(
        colors = colors,
        metrics =
            SegmentedControlButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                segmentedButtonPadding = PaddingValues(horizontal = 14.dp),
                minSize = DpSize(minimumSize.width, minimumSize.height),
                borderWidth = DarculaUIUtil.LW.dp,
            ),
    )
}

private fun readSegmentedControlStyle(): SegmentedControlStyle {
    val normalBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false).toComposeColor(),
            )
            .createVerticalBrush()

    val colors =
        SegmentedControlColors(
            border = normalBorder,
            borderDisabled = SolidColor(JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor()),
            borderPressed = normalBorder,
            borderHovered = normalBorder,
            borderFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
        )

    return SegmentedControlStyle(
        colors = colors,
        metrics =
            SegmentedControlMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                borderWidth = DarculaUIUtil.LW.dp,
            ),
    )
}

private fun readSliderStyle(dark: Boolean): SliderStyle {
    // There are no values for sliders in IntUi, so we're essentially reusing the
    // standalone colors logic, reading the palette values (and falling back to
    // hardcoded defaults).
    val colors = if (dark) SliderColors.dark() else SliderColors.light()
    return SliderStyle(colors, SliderMetrics.defaults(), CircleShape)
}

private fun readTextAreaStyle(metrics: TextFieldMetrics): TextAreaStyle {
    val normalBackground = retrieveColorOrUnspecified("TextArea.background")
    val normalContent = retrieveColorOrUnspecified("TextArea.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextArea.caretForeground")

    val colors =
        TextAreaColors(
            background = normalBackground,
            backgroundDisabled = Color.Unspecified,
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
        metrics =
            TextAreaMetrics(
                cornerSize = metrics.cornerSize,
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
                minSize = metrics.minSize,
                borderWidth = metrics.borderWidth,
            ),
    )
}

private fun readTextFieldStyle(): TextFieldStyle {
    val normalBackground = retrieveColorOrUnspecified("TextField.background")
    val normalContent = retrieveColorOrUnspecified("TextField.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextField.caretForeground")

    val colors =
        TextFieldColors(
            background = normalBackground,
            backgroundDisabled = Color.Unspecified,
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

    val minimumSize = JBUI.CurrentTheme.TextField.minimumSize().toDpSize()
    return TextFieldStyle(
        colors = colors,
        metrics =
            TextFieldMetrics(
                cornerSize = componentArc,
                contentPadding = PaddingValues(horizontal = 8.dp + DarculaUIUtil.LW.dp),
                minSize = DpSize(144.dp, minimumSize.height),
                borderWidth = DarculaUIUtil.LW.dp,
            ),
    )
}

private fun readLazyTreeStyle(): LazyTreeStyle {
    val normalContent = retrieveColorOrUnspecified("Tree.foreground")
    val selectedContent = retrieveColorOrUnspecified("Tree.selectionForeground")
    val selectedElementBackground = retrieveColorOrUnspecified("Tree.selectionBackground")
    val inactiveSelectedElementBackground = retrieveColorOrUnspecified("Tree.selectionInactiveBackground")

    val colors =
        LazyTreeColors(
            content = normalContent,
            contentFocused = normalContent,
            contentSelected = selectedContent,
            contentSelectedFocused = selectedContent,
            elementBackgroundFocused = Color.Unspecified,
            elementBackgroundSelected = inactiveSelectedElementBackground,
            elementBackgroundSelectedFocused = selectedElementBackground,
        )

    val leftIndent = retrieveIntAsDpOrUnspecified("Tree.leftChildIndent").takeOrElse { 7.dp }
    val rightIndent = retrieveIntAsDpOrUnspecified("Tree.rightChildIndent").takeOrElse { 11.dp }

    return LazyTreeStyle(
        colors = colors,
        metrics =
            LazyTreeMetrics(
                indentSize = leftIndent + rightIndent,
                elementBackgroundCornerSize = CornerSize(JBUI.CurrentTheme.Tree.ARC.dp / 2),
                elementPadding = PaddingValues(horizontal = 12.dp),
                elementContentPadding = PaddingValues(4.dp),
                elementMinHeight = retrieveIntAsDpOrUnspecified("Tree.rowHeight").takeOrElse { 24.dp },
                chevronContentGap = 2.dp, // See com.intellij.ui.tree.ui.ClassicPainter.GAP
            ),
        icons =
            LazyTreeIcons(
                chevronCollapsed = AllIconsKeys.General.ChevronRight,
                chevronExpanded = AllIconsKeys.General.ChevronDown,
                chevronSelectedCollapsed = AllIconsKeys.General.ChevronRight,
                chevronSelectedExpanded = AllIconsKeys.General.ChevronDown,
            ),
    )
}

// See com.intellij.ui.tabs.impl.themes.DefaultTabTheme
private fun readDefaultTabStyle(): TabStyle {
    val normalBackground = JBUI.CurrentTheme.DefaultTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.DefaultTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors =
        TabColors(
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
            underline = Color.Unspecified,
            underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
            underlinePressed = selectedUnderline,
            underlineHovered = Color.Unspecified,
            underlineSelected = selectedUnderline,
        )

    return TabStyle(
        colors = colors,
        metrics =
            TabMetrics(
                underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight").takeOrElse { 2.dp },
                tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
                closeContentGap = 4.dp,
                tabContentSpacing = 4.dp,
                tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight").takeOrElse { 24.dp },
            ),
        icons = TabIcons(close = AllIconsKeys.General.CloseSmall),
        contentAlpha =
            TabContentAlpha(
                iconNormal = 1f,
                iconDisabled = 1f,
                iconPressed = 1f,
                iconHovered = 1f,
                iconSelected = 1f,
                contentNormal = 1f,
                contentDisabled = 1f,
                contentPressed = 1f,
                contentHovered = 1f,
                contentSelected = 1f,
            ),
        scrollbarStyle = readScrollbarStyle(isDark),
    )
}

private fun readEditorTabStyle(): TabStyle {
    val normalBackground = JBUI.CurrentTheme.EditorTabs.background().toComposeColor()
    val selectedBackground = JBUI.CurrentTheme.EditorTabs.underlinedTabBackground().toComposeColorOrUnspecified()
    val normalContent = retrieveColorOrUnspecified("TabbedPane.foreground")
    val selectedUnderline = retrieveColorOrUnspecified("TabbedPane.underlineColor")

    val colors =
        TabColors(
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
            underline = Color.Unspecified,
            underlineDisabled = retrieveColorOrUnspecified("TabbedPane.disabledUnderlineColor"),
            underlinePressed = selectedUnderline,
            underlineHovered = Color.Unspecified,
            underlineSelected = selectedUnderline,
        )

    return TabStyle(
        colors = colors,
        metrics =
            TabMetrics(
                underlineThickness = retrieveIntAsDpOrUnspecified("TabbedPane.tabSelectionHeight").takeOrElse { 2.dp },
                tabPadding = retrieveInsetsAsPaddingValues("TabbedPane.tabInsets"),
                closeContentGap = 4.dp,
                tabContentSpacing = 4.dp,
                tabHeight = retrieveIntAsDpOrUnspecified("TabbedPane.tabHeight").takeOrElse { 24.dp },
            ),
        icons = TabIcons(close = AllIconsKeys.General.CloseSmall),
        contentAlpha =
            TabContentAlpha(
                iconNormal = .7f,
                iconDisabled = .7f,
                iconPressed = 1f,
                iconHovered = 1f,
                iconSelected = 1f,
                contentNormal = .7f,
                contentDisabled = .7f,
                contentPressed = 1f,
                contentHovered = 1f,
                contentSelected = 1f,
            ),
        scrollbarStyle = readScrollbarStyle(isDark),
    )
}

private fun readCircularProgressStyle(isDark: Boolean) =
    CircularProgressStyle(
        frameTime = 125.milliseconds,
        color =
            retrieveColorOrUnspecified("ProgressIcon.color").takeOrElse {
                if (isDark) Color(0xFF6F737A) else Color(0xFFA8ADBD)
            },
    )

private fun readTooltipStyle(): TooltipStyle {
    return TooltipStyle(
        metrics =
            TooltipMetrics.defaults(
                contentPadding = JBUI.CurrentTheme.HelpTooltip.smallTextBorderInsets().toPaddingValues(),
                showDelay = Registry.intValue("ide.tooltip.initialDelay").milliseconds,
                cornerSize = CornerSize(JBUI.CurrentTheme.Tooltip.CORNER_RADIUS.dp),
            ),
        colors =
            TooltipColors(
                content = retrieveColorOrUnspecified("ToolTip.foreground"),
                background = retrieveColorOrUnspecified("ToolTip.background"),
                border = JBUI.CurrentTheme.Tooltip.borderColor().toComposeColor(),
                shadow = retrieveColorOrUnspecified("Notification.Shadow.bottom1Color"),
            ),
    )
}

private fun readIconButtonStyle(): IconButtonStyle =
    IconButtonStyle(
        metrics =
            IconButtonMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                borderWidth = 1.dp,
                padding = PaddingValues(0.dp),
                minSize = DpSize(24.dp, 24.dp),
            ),
        colors =
            IconButtonColors(
                foregroundSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedForeground"),
                background = Color.Unspecified,
                backgroundDisabled = Color.Unspecified,
                backgroundSelected = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
                backgroundSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedBackground"),
                backgroundPressed = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
                backgroundHovered = retrieveColorOrUnspecified("ActionButton.hoverBackground"),
                backgroundFocused = retrieveColorOrUnspecified("ActionButton.hoverBackground"),
                border = Color.Unspecified,
                borderDisabled = Color.Unspecified,
                borderSelected = retrieveColorOrUnspecified("ActionButton.pressedBackground"),
                borderSelectedActivated = retrieveColorOrUnspecified("ToolWindow.Button.selectedBackground"),
                borderFocused = Color.Unspecified,
                borderPressed = retrieveColorOrUnspecified("ActionButton.pressedBorderColor"),
                borderHovered = retrieveColorOrUnspecified("ActionButton.hoverBorderColor"),
            ),
    )

private val componentArc: CornerSize
    get() = CornerSize(DarculaUIUtil.COMPONENT_ARC.dp / 2)
