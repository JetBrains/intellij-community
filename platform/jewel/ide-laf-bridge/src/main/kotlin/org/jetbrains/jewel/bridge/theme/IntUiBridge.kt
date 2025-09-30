package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.DirProvider
import javax.swing.UIManager
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.lafName
import org.jetbrains.jewel.bridge.readFromLaF
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.foundation.util.JewelLogger
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.DefaultComponentStyling

private val logger = JewelLogger.getInstance("JewelIntUiBridge")

internal val uiDefaults
    get() = UIManager.getDefaults()

internal val iconsBasePath
    get() = DirProvider().dir()

internal fun createBridgeThemeDefinition(): ThemeDefinition {
    val textStyle = retrieveDefaultTextStyle()
    val editorTextStyle = retrieveEditorTextStyle()
    val consoleTextStyle = retrieveConsoleTextStyle()
    return createBridgeThemeDefinition(textStyle, editorTextStyle, consoleTextStyle)
}

internal val isDark: Boolean
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
        disabledAppearanceValues = DisabledAppearanceValues.readFromLaF(),
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
        comboBoxStyle = readDefaultComboBoxStyle(),
        defaultBannerStyle = readDefaultBannerStyle(),
        defaultButtonStyle = readDefaultButtonStyle(),
        defaultDropdownStyle = readDefaultDropdownStyle(menuStyle),
        defaultSplitButtonStyle = readDefaultSplitButtonStyle(),
        defaultTabStyle = readDefaultTabStyle(),
        dividerStyle = readDividerStyle(),
        editorTabStyle = readEditorTabStyle(),
        groupHeaderStyle = readGroupHeaderStyle(),
        horizontalProgressBarStyle = readHorizontalProgressBarStyle(),
        iconButtonStyle = readIconButtonStyle(),
        transparentIconButtonStyle = readTransparentIconButton(),
        inlineBannerStyle = readInlineBannerStyle(),
        lazyTreeStyle = readLazyTreeStyle(),
        linkStyle = readLinkStyle(),
        menuStyle = menuStyle,
        outlinedButtonStyle = readOutlinedButtonStyle(),
        outlinedSplitButtonStyle = readOutlinedSplitButtonStyle(),
        popupContainerStyle = readPopupContainerStyle(),
        radioButtonStyle = readRadioButtonStyle(),
        scrollbarStyle = readScrollbarStyle(theme.isDark),
        segmentedControlButtonStyle = readSegmentedControlButtonStyle(),
        segmentedControlStyle = readSegmentedControlStyle(),
        selectableLazyColumnStyle = readSelectableLazyColumnStyle(),
        simpleListItemStyle = readSimpleListItemStyle(),
        sliderStyle = readSliderStyle(theme.isDark),
        textAreaStyle = readTextAreaStyle(textFieldStyle.metrics),
        textFieldStyle = textFieldStyle,
        tooltipStyle = readTooltipStyle(),
        undecoratedDropdownStyle = readUndecoratedDropdownStyle(menuStyle),
    )
}

internal val componentArc: CornerSize
    get() = CornerSize(DarculaUIUtil.COMPONENT_ARC.dp.safeValue() / 2)

internal val borderWidth: Dp
    get() = DarculaUIUtil.LW.dp.safeValue()
