@file:Suppress("DuplicatedCode")

package org.jetbrains.jewel.intui.standalone.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.intui.standalone.IntUiMessageResourceResolver
import org.jetbrains.jewel.intui.standalone.IntUiTypography
import org.jetbrains.jewel.intui.standalone.StandalonePainterHintsProvider
import org.jetbrains.jewel.intui.standalone.icon.StandaloneNewUiChecker
import org.jetbrains.jewel.intui.standalone.menuShortcut.StandaloneMenuItemShortcutHintProvider
import org.jetbrains.jewel.intui.standalone.menuShortcut.StandaloneShortcutProvider
import org.jetbrains.jewel.intui.standalone.styling.Default
import org.jetbrains.jewel.intui.standalone.styling.Editor
import org.jetbrains.jewel.intui.standalone.styling.Outlined
import org.jetbrains.jewel.intui.standalone.styling.Undecorated
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.darkTransparentBackground
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.styling.lightTransparentBackground
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.DefaultComponentStyling
import org.jetbrains.jewel.ui.LocalMenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.LocalMenuItemShortcutProvider
import org.jetbrains.jewel.ui.LocalTypography
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.component.styling.ChipStyle
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.DefaultBannerStyles
import org.jetbrains.jewel.ui.component.styling.DividerStyle
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.InlineBannerStyles
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.component.styling.RadioButtonStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlStyle
import org.jetbrains.jewel.ui.component.styling.SelectableLazyColumnStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.component.styling.SliderStyle
import org.jetbrains.jewel.ui.component.styling.SplitButtonStyle
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TooltipAutoHideBehavior
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.icon.LocalNewUiChecker
import org.jetbrains.jewel.ui.painter.LocalPainterHintsProvider
import org.jetbrains.jewel.ui.theme.BaseJewelTheme
import org.jetbrains.jewel.ui.util.LocalMessageResourceResolverProvider

/**
 * Create a light theme definition.
 *
 * @param colors The [GlobalColors] for this theme definition.
 * @param metrics The [GlobalMetrics] for this theme definition.
 * @param palette The [ThemeColorPalette] for this theme definition.
 * @param iconData The [ThemeIconData] for this theme definition.
 * @param defaultTextStyle The default text style for this theme definition.
 * @param editorTextStyle The editor text style for this theme definition.
 * @param consoleTextStyle The console text style for this theme definition. Same as [editorTextStyle] by default.
 * @param contentColor The default content (text) color for this theme definition.
 * @param disabledAppearanceValues The [DisabledAppearanceValues] for this theme definition.
 */
public fun JewelTheme.Companion.lightThemeDefinition(
    colors: GlobalColors = GlobalColors.light(),
    metrics: GlobalMetrics = GlobalMetrics.defaults(),
    palette: ThemeColorPalette = IntUiLightTheme.colors,
    iconData: ThemeIconData = IntUiLightTheme.iconData,
    defaultTextStyle: TextStyle = JewelTheme.createDefaultTextStyle(),
    editorTextStyle: TextStyle = JewelTheme.createEditorTextStyle(),
    consoleTextStyle: TextStyle = editorTextStyle,
    contentColor: Color = colors.text.normal,
    disabledAppearanceValues: DisabledAppearanceValues = DisabledAppearanceValues.light(),
): ThemeDefinition =
    ThemeDefinition(
        name = "IntUI Light",
        isDark = false,
        colors,
        metrics,
        defaultTextStyle,
        editorTextStyle,
        consoleTextStyle,
        contentColor,
        palette,
        iconData,
        disabledAppearanceValues,
    )

/**
 * Create a light theme definition.
 *
 * @param colors The [GlobalColors] for this theme definition.
 * @param metrics The [GlobalMetrics] for this theme definition.
 * @param palette The [ThemeColorPalette] for this theme definition.
 * @param iconData The [ThemeIconData] for this theme definition.
 * @param defaultTextStyle The default text style for this theme definition.
 * @param editorTextStyle The editor text style for this theme definition.
 * @param consoleTextStyle The console text style for this theme definition. Same as [editorTextStyle] by default.
 * @param contentColor The default content (text) color for this theme definition.
 */
@Deprecated("Please, use the overload with disabledAppearanceValues.")
public fun JewelTheme.Companion.lightThemeDefinition(
    colors: GlobalColors = GlobalColors.light(),
    metrics: GlobalMetrics = GlobalMetrics.defaults(),
    palette: ThemeColorPalette = IntUiLightTheme.colors,
    iconData: ThemeIconData = IntUiLightTheme.iconData,
    defaultTextStyle: TextStyle = JewelTheme.createDefaultTextStyle(),
    editorTextStyle: TextStyle = JewelTheme.createEditorTextStyle(),
    consoleTextStyle: TextStyle = editorTextStyle,
    contentColor: Color = colors.text.normal,
): ThemeDefinition =
    ThemeDefinition(
        name = "IntUI Light",
        isDark = false,
        colors,
        metrics,
        defaultTextStyle,
        editorTextStyle,
        consoleTextStyle,
        contentColor,
        palette,
        iconData,
        DisabledAppearanceValues.light(),
    )

/**
 * Create a dark theme definition.
 *
 * @param colors The [GlobalColors] for this theme definition.
 * @param metrics The [GlobalMetrics] for this theme definition.
 * @param palette The [ThemeColorPalette] for this theme definition.
 * @param iconData The [ThemeIconData] for this theme definition.
 * @param defaultTextStyle The default text style for this theme definition.
 * @param editorTextStyle The editor text style for this theme definition.
 * @param consoleTextStyle The console text style for this theme definition. Same as [editorTextStyle] by default.
 * @param contentColor The default content (text) color for this theme definition.
 */
@Deprecated("Please, use the overload with disabledAppearanceValues.")
public fun JewelTheme.Companion.darkThemeDefinition(
    colors: GlobalColors = GlobalColors.dark(),
    metrics: GlobalMetrics = GlobalMetrics.defaults(),
    palette: ThemeColorPalette = IntUiDarkTheme.colors,
    iconData: ThemeIconData = IntUiDarkTheme.iconData,
    defaultTextStyle: TextStyle = JewelTheme.createDefaultTextStyle(),
    editorTextStyle: TextStyle = JewelTheme.createEditorTextStyle(),
    consoleTextStyle: TextStyle = editorTextStyle,
    contentColor: Color = colors.text.normal,
): ThemeDefinition =
    ThemeDefinition(
        name = "IntUI Dark",
        isDark = true,
        colors,
        metrics,
        defaultTextStyle,
        editorTextStyle,
        consoleTextStyle,
        contentColor,
        palette,
        iconData,
        DisabledAppearanceValues.dark(),
    )

/**
 * Create a dark theme definition.
 *
 * @param colors The [GlobalColors] for this theme definition.
 * @param metrics The [GlobalMetrics] for this theme definition.
 * @param palette The [ThemeColorPalette] for this theme definition.
 * @param iconData The [ThemeIconData] for this theme definition.
 * @param defaultTextStyle The default text style for this theme definition.
 * @param editorTextStyle The editor text style for this theme definition.
 * @param consoleTextStyle The console text style for this theme definition. Same as [editorTextStyle] by default.
 * @param contentColor The default content (text) color for this theme definition.
 * @param disabledAppearanceValues The [DisabledAppearanceValues] for this theme definition.
 */
public fun JewelTheme.Companion.darkThemeDefinition(
    colors: GlobalColors = GlobalColors.dark(),
    metrics: GlobalMetrics = GlobalMetrics.defaults(),
    palette: ThemeColorPalette = IntUiDarkTheme.colors,
    iconData: ThemeIconData = IntUiDarkTheme.iconData,
    defaultTextStyle: TextStyle = JewelTheme.createDefaultTextStyle(),
    editorTextStyle: TextStyle = JewelTheme.createEditorTextStyle(),
    consoleTextStyle: TextStyle = editorTextStyle,
    contentColor: Color = colors.text.normal,
    disabledAppearanceValues: DisabledAppearanceValues = DisabledAppearanceValues.dark(),
): ThemeDefinition =
    ThemeDefinition(
        name = "IntUI Dark",
        isDark = true,
        colors,
        metrics,
        defaultTextStyle,
        editorTextStyle,
        consoleTextStyle,
        contentColor,
        palette,
        iconData,
        disabledAppearanceValues,
    )

@Composable
public fun ComponentStyling.default(): ComponentStyling = with {
    val isDark = JewelTheme.isDark
    remember(JewelTheme.instanceUuid) {
        if (isDark) {
            dark(transparentIconButtonStyle = IconButtonStyle.darkTransparentBackground())
        } else {
            light(transparentIconButtonStyle = IconButtonStyle.lightTransparentBackground())
        }
    }
}

@Suppress("UnusedReceiverParameter")
public fun ComponentStyling.dark(
    checkboxStyle: CheckboxStyle = CheckboxStyle.dark(),
    chipStyle: ChipStyle = ChipStyle.dark(),
    circularProgressStyle: CircularProgressStyle = CircularProgressStyle.dark(),
    defaultBannerStyle: DefaultBannerStyles = DefaultBannerStyles.Default.dark(),
    comboBoxStyle: ComboBoxStyle = ComboBoxStyle.Default.dark(),
    defaultButtonStyle: ButtonStyle = ButtonStyle.Default.dark(),
    defaultSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Default.dark(),
    defaultTabStyle: TabStyle = TabStyle.Default.dark(),
    dividerStyle: DividerStyle = DividerStyle.dark(),
    dropdownStyle: DropdownStyle = DropdownStyle.Default.dark(),
    editorTabStyle: TabStyle = TabStyle.Editor.dark(),
    groupHeaderStyle: GroupHeaderStyle = GroupHeaderStyle.dark(),
    horizontalProgressBarStyle: HorizontalProgressBarStyle = HorizontalProgressBarStyle.dark(),
    iconButtonStyle: IconButtonStyle = IconButtonStyle.dark(),
    transparentIconButtonStyle: IconButtonStyle = IconButtonStyle.darkTransparentBackground(),
    inlineBannerStyle: InlineBannerStyles = InlineBannerStyles.Default.dark(),
    lazyTreeStyle: LazyTreeStyle = LazyTreeStyle.dark(),
    linkStyle: LinkStyle = LinkStyle.dark(),
    menuStyle: MenuStyle = MenuStyle.dark(),
    outlinedButtonStyle: ButtonStyle = ButtonStyle.Outlined.dark(),
    popupContainerStyle: PopupContainerStyle = PopupContainerStyle.dark(),
    outlinedSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Outlined.dark(),
    radioButtonStyle: RadioButtonStyle = RadioButtonStyle.dark(),
    scrollbarStyle: ScrollbarStyle = ScrollbarStyle.dark(),
    segmentedControlButtonStyle: SegmentedControlButtonStyle = SegmentedControlButtonStyle.dark(),
    segmentedControlStyle: SegmentedControlStyle = SegmentedControlStyle.dark(),
    selectableLazyColumnStyle: SelectableLazyColumnStyle = SelectableLazyColumnStyle.dark(),
    sliderStyle: SliderStyle = SliderStyle.dark(),
    simpleListItemStyle: SimpleListItemStyle = SimpleListItemStyle.dark(),
    textAreaStyle: TextAreaStyle = TextAreaStyle.dark(),
    textFieldStyle: TextFieldStyle = TextFieldStyle.dark(),
    tooltipStyle: TooltipStyle = TooltipStyle.dark(autoHideBehavior = TooltipAutoHideBehavior.Normal),
    undecoratedDropdownStyle: DropdownStyle = DropdownStyle.Undecorated.dark(),
): ComponentStyling =
    DefaultComponentStyling(
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        circularProgressStyle = circularProgressStyle,
        defaultBannerStyle = defaultBannerStyle,
        comboBoxStyle = comboBoxStyle,
        defaultButtonStyle = defaultButtonStyle,
        defaultDropdownStyle = dropdownStyle,
        defaultSplitButtonStyle = defaultSplitButtonStyle,
        defaultTabStyle = defaultTabStyle,
        dividerStyle = dividerStyle,
        editorTabStyle = editorTabStyle,
        groupHeaderStyle = groupHeaderStyle,
        horizontalProgressBarStyle = horizontalProgressBarStyle,
        iconButtonStyle = iconButtonStyle,
        transparentIconButtonStyle = transparentIconButtonStyle,
        inlineBannerStyle = inlineBannerStyle,
        lazyTreeStyle = lazyTreeStyle,
        linkStyle = linkStyle,
        menuStyle = menuStyle,
        outlinedButtonStyle = outlinedButtonStyle,
        popupContainerStyle = popupContainerStyle,
        outlinedSplitButtonStyle = outlinedSplitButtonStyle,
        radioButtonStyle = radioButtonStyle,
        scrollbarStyle = scrollbarStyle,
        segmentedControlButtonStyle = segmentedControlButtonStyle,
        segmentedControlStyle = segmentedControlStyle,
        selectableLazyColumnStyle = selectableLazyColumnStyle,
        simpleListItemStyle = simpleListItemStyle,
        sliderStyle = sliderStyle,
        textAreaStyle = textAreaStyle,
        textFieldStyle = textFieldStyle,
        tooltipStyle = tooltipStyle,
        undecoratedDropdownStyle = undecoratedDropdownStyle,
    )

@Suppress("UnusedReceiverParameter", "DEPRECATION_ERROR")
@Deprecated("Use the variant with transparentIconButtonStyle.", level = DeprecationLevel.ERROR)
public fun ComponentStyling.dark(
    checkboxStyle: CheckboxStyle = CheckboxStyle.dark(),
    chipStyle: ChipStyle = ChipStyle.dark(),
    circularProgressStyle: CircularProgressStyle = CircularProgressStyle.dark(),
    defaultBannerStyle: DefaultBannerStyles = DefaultBannerStyles.Default.dark(),
    comboBoxStyle: ComboBoxStyle = ComboBoxStyle.Default.dark(),
    defaultButtonStyle: ButtonStyle = ButtonStyle.Default.dark(),
    defaultSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Default.dark(),
    defaultTabStyle: TabStyle = TabStyle.Default.dark(),
    dividerStyle: DividerStyle = DividerStyle.dark(),
    dropdownStyle: DropdownStyle = DropdownStyle.Default.dark(),
    editorTabStyle: TabStyle = TabStyle.Editor.dark(),
    groupHeaderStyle: GroupHeaderStyle = GroupHeaderStyle.dark(),
    horizontalProgressBarStyle: HorizontalProgressBarStyle = HorizontalProgressBarStyle.dark(),
    iconButtonStyle: IconButtonStyle = IconButtonStyle.dark(),
    inlineBannerStyle: InlineBannerStyles = InlineBannerStyles.Default.dark(),
    lazyTreeStyle: LazyTreeStyle = LazyTreeStyle.dark(),
    linkStyle: LinkStyle = LinkStyle.dark(),
    menuStyle: MenuStyle = MenuStyle.dark(),
    outlinedButtonStyle: ButtonStyle = ButtonStyle.Outlined.dark(),
    popupContainerStyle: PopupContainerStyle = PopupContainerStyle.dark(),
    outlinedSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Outlined.dark(),
    radioButtonStyle: RadioButtonStyle = RadioButtonStyle.dark(),
    scrollbarStyle: ScrollbarStyle = ScrollbarStyle.dark(),
    segmentedControlButtonStyle: SegmentedControlButtonStyle = SegmentedControlButtonStyle.dark(),
    segmentedControlStyle: SegmentedControlStyle = SegmentedControlStyle.dark(),
    selectableLazyColumnStyle: SelectableLazyColumnStyle = SelectableLazyColumnStyle.dark(),
    sliderStyle: SliderStyle = SliderStyle.dark(),
    simpleListItemStyle: SimpleListItemStyle = SimpleListItemStyle.dark(),
    textAreaStyle: TextAreaStyle = TextAreaStyle.dark(),
    textFieldStyle: TextFieldStyle = TextFieldStyle.dark(),
    tooltipStyle: TooltipStyle = TooltipStyle.dark(autoHideBehavior = TooltipAutoHideBehavior.Normal),
    undecoratedDropdownStyle: DropdownStyle = DropdownStyle.Undecorated.dark(),
): ComponentStyling =
    DefaultComponentStyling(
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        circularProgressStyle = circularProgressStyle,
        defaultBannerStyle = defaultBannerStyle,
        comboBoxStyle = comboBoxStyle,
        defaultButtonStyle = defaultButtonStyle,
        defaultDropdownStyle = dropdownStyle,
        defaultSplitButtonStyle = defaultSplitButtonStyle,
        defaultTabStyle = defaultTabStyle,
        dividerStyle = dividerStyle,
        editorTabStyle = editorTabStyle,
        groupHeaderStyle = groupHeaderStyle,
        horizontalProgressBarStyle = horizontalProgressBarStyle,
        iconButtonStyle = iconButtonStyle,
        inlineBannerStyle = inlineBannerStyle,
        lazyTreeStyle = lazyTreeStyle,
        linkStyle = linkStyle,
        menuStyle = menuStyle,
        outlinedButtonStyle = outlinedButtonStyle,
        popupContainerStyle = popupContainerStyle,
        outlinedSplitButtonStyle = outlinedSplitButtonStyle,
        radioButtonStyle = radioButtonStyle,
        scrollbarStyle = scrollbarStyle,
        segmentedControlButtonStyle = segmentedControlButtonStyle,
        segmentedControlStyle = segmentedControlStyle,
        selectableLazyColumnStyle = selectableLazyColumnStyle,
        simpleListItemStyle = simpleListItemStyle,
        sliderStyle = sliderStyle,
        textAreaStyle = textAreaStyle,
        textFieldStyle = textFieldStyle,
        tooltipStyle = tooltipStyle,
        undecoratedDropdownStyle = undecoratedDropdownStyle,
    )

@Suppress("UnusedReceiverParameter")
public fun ComponentStyling.light(
    checkboxStyle: CheckboxStyle = CheckboxStyle.light(),
    chipStyle: ChipStyle = ChipStyle.light(),
    circularProgressStyle: CircularProgressStyle = CircularProgressStyle.light(),
    defaultBannerStyle: DefaultBannerStyles = DefaultBannerStyles.Default.light(),
    comboBoxStyle: ComboBoxStyle = ComboBoxStyle.Default.light(),
    defaultButtonStyle: ButtonStyle = ButtonStyle.Default.light(),
    defaultSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Default.light(),
    defaultTabStyle: TabStyle = TabStyle.Default.light(),
    dividerStyle: DividerStyle = DividerStyle.light(),
    dropdownStyle: DropdownStyle = DropdownStyle.Default.light(),
    editorTabStyle: TabStyle = TabStyle.Editor.light(),
    groupHeaderStyle: GroupHeaderStyle = GroupHeaderStyle.light(),
    horizontalProgressBarStyle: HorizontalProgressBarStyle = HorizontalProgressBarStyle.light(),
    iconButtonStyle: IconButtonStyle = IconButtonStyle.light(),
    transparentIconButtonStyle: IconButtonStyle = IconButtonStyle.lightTransparentBackground(),
    inlineBannerStyle: InlineBannerStyles = InlineBannerStyles.Default.light(),
    lazyTreeStyle: LazyTreeStyle = LazyTreeStyle.light(),
    linkStyle: LinkStyle = LinkStyle.light(),
    menuStyle: MenuStyle = MenuStyle.light(),
    popupContainerStyle: PopupContainerStyle = PopupContainerStyle.light(),
    outlinedButtonStyle: ButtonStyle = ButtonStyle.Outlined.light(),
    outlinedSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Outlined.light(),
    radioButtonStyle: RadioButtonStyle = RadioButtonStyle.light(),
    scrollbarStyle: ScrollbarStyle = ScrollbarStyle.light(),
    segmentedControlButtonStyle: SegmentedControlButtonStyle = SegmentedControlButtonStyle.light(),
    segmentedControlStyle: SegmentedControlStyle = SegmentedControlStyle.light(),
    sliderStyle: SliderStyle = SliderStyle.light(),
    selectableLazyColumnStyle: SelectableLazyColumnStyle = SelectableLazyColumnStyle.light(),
    simpleListItemStyle: SimpleListItemStyle = SimpleListItemStyle.light(),
    textAreaStyle: TextAreaStyle = TextAreaStyle.light(),
    textFieldStyle: TextFieldStyle = TextFieldStyle.light(),
    tooltipStyle: TooltipStyle = TooltipStyle.light(autoHideBehavior = TooltipAutoHideBehavior.Normal),
    undecoratedDropdownStyle: DropdownStyle = DropdownStyle.Undecorated.light(),
): ComponentStyling =
    DefaultComponentStyling(
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        circularProgressStyle = circularProgressStyle,
        comboBoxStyle = comboBoxStyle,
        defaultBannerStyle = defaultBannerStyle,
        defaultButtonStyle = defaultButtonStyle,
        defaultDropdownStyle = dropdownStyle,
        defaultSplitButtonStyle = defaultSplitButtonStyle,
        defaultTabStyle = defaultTabStyle,
        dividerStyle = dividerStyle,
        editorTabStyle = editorTabStyle,
        groupHeaderStyle = groupHeaderStyle,
        horizontalProgressBarStyle = horizontalProgressBarStyle,
        iconButtonStyle = iconButtonStyle,
        transparentIconButtonStyle = transparentIconButtonStyle,
        inlineBannerStyle = inlineBannerStyle,
        lazyTreeStyle = lazyTreeStyle,
        linkStyle = linkStyle,
        menuStyle = menuStyle,
        outlinedButtonStyle = outlinedButtonStyle,
        popupContainerStyle = popupContainerStyle,
        outlinedSplitButtonStyle = outlinedSplitButtonStyle,
        radioButtonStyle = radioButtonStyle,
        scrollbarStyle = scrollbarStyle,
        segmentedControlButtonStyle = segmentedControlButtonStyle,
        segmentedControlStyle = segmentedControlStyle,
        selectableLazyColumnStyle = selectableLazyColumnStyle,
        sliderStyle = sliderStyle,
        simpleListItemStyle = simpleListItemStyle,
        textAreaStyle = textAreaStyle,
        textFieldStyle = textFieldStyle,
        tooltipStyle = tooltipStyle,
        undecoratedDropdownStyle = undecoratedDropdownStyle,
    )

@Suppress("UnusedReceiverParameter", "DEPRECATION_ERROR")
@Deprecated("Use the variant with transparentIconButtonStyle.", level = DeprecationLevel.ERROR)
public fun ComponentStyling.light(
    checkboxStyle: CheckboxStyle = CheckboxStyle.light(),
    chipStyle: ChipStyle = ChipStyle.light(),
    circularProgressStyle: CircularProgressStyle = CircularProgressStyle.light(),
    defaultBannerStyle: DefaultBannerStyles = DefaultBannerStyles.Default.light(),
    comboBoxStyle: ComboBoxStyle = ComboBoxStyle.Default.light(),
    defaultButtonStyle: ButtonStyle = ButtonStyle.Default.light(),
    defaultSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Default.light(),
    defaultTabStyle: TabStyle = TabStyle.Default.light(),
    dividerStyle: DividerStyle = DividerStyle.light(),
    dropdownStyle: DropdownStyle = DropdownStyle.Default.light(),
    editorTabStyle: TabStyle = TabStyle.Editor.light(),
    groupHeaderStyle: GroupHeaderStyle = GroupHeaderStyle.light(),
    horizontalProgressBarStyle: HorizontalProgressBarStyle = HorizontalProgressBarStyle.light(),
    iconButtonStyle: IconButtonStyle = IconButtonStyle.light(),
    inlineBannerStyle: InlineBannerStyles = InlineBannerStyles.Default.light(),
    lazyTreeStyle: LazyTreeStyle = LazyTreeStyle.light(),
    linkStyle: LinkStyle = LinkStyle.light(),
    menuStyle: MenuStyle = MenuStyle.light(),
    popupContainerStyle: PopupContainerStyle = PopupContainerStyle.light(),
    outlinedButtonStyle: ButtonStyle = ButtonStyle.Outlined.light(),
    outlinedSplitButtonStyle: SplitButtonStyle = SplitButtonStyle.Outlined.light(),
    radioButtonStyle: RadioButtonStyle = RadioButtonStyle.light(),
    scrollbarStyle: ScrollbarStyle = ScrollbarStyle.light(),
    segmentedControlButtonStyle: SegmentedControlButtonStyle = SegmentedControlButtonStyle.light(),
    segmentedControlStyle: SegmentedControlStyle = SegmentedControlStyle.light(),
    sliderStyle: SliderStyle = SliderStyle.light(),
    selectableLazyColumnStyle: SelectableLazyColumnStyle = SelectableLazyColumnStyle.light(),
    simpleListItemStyle: SimpleListItemStyle = SimpleListItemStyle.light(),
    textAreaStyle: TextAreaStyle = TextAreaStyle.light(),
    textFieldStyle: TextFieldStyle = TextFieldStyle.light(),
    tooltipStyle: TooltipStyle = TooltipStyle.light(autoHideBehavior = TooltipAutoHideBehavior.Normal),
    undecoratedDropdownStyle: DropdownStyle = DropdownStyle.Undecorated.light(),
): ComponentStyling =
    DefaultComponentStyling(
        checkboxStyle = checkboxStyle,
        chipStyle = chipStyle,
        circularProgressStyle = circularProgressStyle,
        comboBoxStyle = comboBoxStyle,
        defaultBannerStyle = defaultBannerStyle,
        defaultButtonStyle = defaultButtonStyle,
        defaultDropdownStyle = dropdownStyle,
        defaultSplitButtonStyle = defaultSplitButtonStyle,
        defaultTabStyle = defaultTabStyle,
        dividerStyle = dividerStyle,
        editorTabStyle = editorTabStyle,
        groupHeaderStyle = groupHeaderStyle,
        horizontalProgressBarStyle = horizontalProgressBarStyle,
        iconButtonStyle = iconButtonStyle,
        inlineBannerStyle = inlineBannerStyle,
        lazyTreeStyle = lazyTreeStyle,
        linkStyle = linkStyle,
        menuStyle = menuStyle,
        outlinedButtonStyle = outlinedButtonStyle,
        popupContainerStyle = popupContainerStyle,
        outlinedSplitButtonStyle = outlinedSplitButtonStyle,
        radioButtonStyle = radioButtonStyle,
        scrollbarStyle = scrollbarStyle,
        segmentedControlButtonStyle = segmentedControlButtonStyle,
        segmentedControlStyle = segmentedControlStyle,
        selectableLazyColumnStyle = selectableLazyColumnStyle,
        sliderStyle = sliderStyle,
        simpleListItemStyle = simpleListItemStyle,
        textAreaStyle = textAreaStyle,
        textFieldStyle = textFieldStyle,
        tooltipStyle = tooltipStyle,
        undecoratedDropdownStyle = undecoratedDropdownStyle,
    )

@Composable
public fun IntUiTheme(isDark: Boolean = false, swingCompatMode: Boolean = false, content: @Composable () -> Unit) {
    val themeDefinition =
        remember(JewelTheme.instanceUuid) {
            if (isDark) {
                JewelTheme.darkThemeDefinition(disabledAppearanceValues = DisabledAppearanceValues.dark())
            } else {
                JewelTheme.lightThemeDefinition(disabledAppearanceValues = DisabledAppearanceValues.light())
            }
        }

    IntUiTheme(
        theme = themeDefinition,
        styling = ComponentStyling,
        swingCompatMode = swingCompatMode,
        content = content,
    )
}

@Composable
public fun IntUiTheme(
    theme: ThemeDefinition,
    styling: ComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    BaseJewelTheme(theme, ComponentStyling.default().with(styling), swingCompatMode) {
        CompositionLocalProvider(
            LocalPainterHintsProvider provides StandalonePainterHintsProvider(theme),
            LocalNewUiChecker provides StandaloneNewUiChecker,
            LocalMenuItemShortcutProvider provides StandaloneShortcutProvider,
            LocalMenuItemShortcutHintProvider provides StandaloneMenuItemShortcutHintProvider,
            LocalTypography provides IntUiTypography,
            LocalMessageResourceResolverProvider provides IntUiMessageResourceResolver(),
        ) {
            content()
        }
    }
}
