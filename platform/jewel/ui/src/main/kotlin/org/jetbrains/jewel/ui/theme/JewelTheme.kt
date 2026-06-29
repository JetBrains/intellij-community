package org.jetbrains.jewel.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalColorPalette
import org.jetbrains.jewel.foundation.theme.LocalIconData
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.styling.BadgeStyles
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
import org.jetbrains.jewel.ui.component.styling.LocalBadgeStyle
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.component.styling.LocalChipStyle
import org.jetbrains.jewel.ui.component.styling.LocalCircularProgressStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultBannerStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultDropdownStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultSlimButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultSplitButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalDividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalEditorTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalGroupHeaderStyle
import org.jetbrains.jewel.ui.component.styling.LocalHorizontalProgressBarStyle
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalInlineBannerStyle
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LocalLinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalMenuStyle
import org.jetbrains.jewel.ui.component.styling.LocalOutlinedButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalOutlinedSlimButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalOutlinedSplitButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalPopupAdStyle
import org.jetbrains.jewel.ui.component.styling.LocalPopupContainerStyle
import org.jetbrains.jewel.ui.component.styling.LocalRadioButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.LocalSearchMatchStyle
import org.jetbrains.jewel.ui.component.styling.LocalSegmentedControlButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalSegmentedControlStyle
import org.jetbrains.jewel.ui.component.styling.LocalSelectableLazyColumnStyle
import org.jetbrains.jewel.ui.component.styling.LocalSimpleListItemStyleStyle
import org.jetbrains.jewel.ui.component.styling.LocalSliderStyle
import org.jetbrains.jewel.ui.component.styling.LocalSpeedSearchStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextAreaStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextFieldStyle
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle
import org.jetbrains.jewel.ui.component.styling.LocalTransparentIconButtonStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.PopupAdStyle
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.component.styling.RadioButtonStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlButtonStyle
import org.jetbrains.jewel.ui.component.styling.SegmentedControlStyle
import org.jetbrains.jewel.ui.component.styling.SelectableLazyColumnStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.component.styling.SliderStyle
import org.jetbrains.jewel.ui.component.styling.SpeedSearchStyle
import org.jetbrains.jewel.ui.component.styling.SplitButtonStyle
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

/** The color palette for the current theme. */
public val JewelTheme.Companion.colorPalette: ThemeColorPalette
    @Composable @ReadOnlyComposable get() = LocalColorPalette.current

/** The icon data for the current theme. */
public val JewelTheme.Companion.iconData: ThemeIconData
    @Composable @ReadOnlyComposable get() = LocalIconData.current

// -----------------
// Component styling
// -----------------

/** The styling for default banner components. */
public val JewelTheme.Companion.defaultBannerStyle: DefaultBannerStyles
    @Composable @ReadOnlyComposable get() = LocalDefaultBannerStyle.current

/** The styling for the default (filled) button. */
public val JewelTheme.Companion.defaultButtonStyle: ButtonStyle
    @Composable @ReadOnlyComposable get() = LocalDefaultButtonStyle.current

/** The styling for the outlined button. */
public val JewelTheme.Companion.outlinedButtonStyle: ButtonStyle
    @Composable @ReadOnlyComposable get() = LocalOutlinedButtonStyle.current

/** The styling for the default (filled) split button. */
public val JewelTheme.Companion.defaultSplitButtonStyle: SplitButtonStyle
    @Composable @ReadOnlyComposable get() = LocalDefaultSplitButtonStyle.current

/** The styling for the outlined split button. */
public val JewelTheme.Companion.outlinedSplitButtonStyle: SplitButtonStyle
    @Composable @ReadOnlyComposable get() = LocalOutlinedSplitButtonStyle.current

/** The styling for checkbox components. */
public val JewelTheme.Companion.checkboxStyle: CheckboxStyle
    @Composable @ReadOnlyComposable get() = LocalCheckboxStyle.current

/** The styling for chip components. */
public val JewelTheme.Companion.chipStyle: ChipStyle
    @Composable @ReadOnlyComposable get() = LocalChipStyle.current

/** The styling for divider components. */
public val JewelTheme.Companion.dividerStyle: DividerStyle
    @Composable @ReadOnlyComposable get() = LocalDividerStyle.current

/** The styling for dropdown components. */
public val JewelTheme.Companion.dropdownStyle: DropdownStyle
    @Composable @ReadOnlyComposable get() = LocalDefaultDropdownStyle.current

/** The styling for combo box components. */
public val JewelTheme.Companion.comboBoxStyle: ComboBoxStyle
    @Composable @ReadOnlyComposable get() = LocalDefaultComboBoxStyle.current

/** The styling for group header components. */
public val JewelTheme.Companion.groupHeaderStyle: GroupHeaderStyle
    @Composable @ReadOnlyComposable get() = LocalGroupHeaderStyle.current

/** The styling for inline banner components. */
public val JewelTheme.Companion.inlineBannerStyle: InlineBannerStyles
    @Composable @ReadOnlyComposable get() = LocalInlineBannerStyle.current

/** The styling for link components. */
public val JewelTheme.Companion.linkStyle: LinkStyle
    @Composable @ReadOnlyComposable get() = LocalLinkStyle.current

/** The styling for menu components. */
public val JewelTheme.Companion.menuStyle: MenuStyle
    @Composable @ReadOnlyComposable get() = LocalMenuStyle.current

/** The styling for popup container components. */
public val JewelTheme.Companion.popupContainerStyle: PopupContainerStyle
    @Composable @ReadOnlyComposable get() = LocalPopupContainerStyle.current

/** The styling for horizontal progress bar components. */
public val JewelTheme.Companion.horizontalProgressBarStyle: HorizontalProgressBarStyle
    @Composable @ReadOnlyComposable get() = LocalHorizontalProgressBarStyle.current

/** The styling for radio button components. */
public val JewelTheme.Companion.radioButtonStyle: RadioButtonStyle
    @Composable @ReadOnlyComposable get() = LocalRadioButtonStyle.current

/** The styling for scrollbar components. */
public val JewelTheme.Companion.scrollbarStyle: ScrollbarStyle
    @Composable @ReadOnlyComposable get() = LocalScrollbarStyle.current

/** The styling for selectable lazy column components. */
public val JewelTheme.Companion.selectableLazyColumnStyle: SelectableLazyColumnStyle
    @Composable @ReadOnlyComposable get() = LocalSelectableLazyColumnStyle.current

/** The styling for segmented control button components. */
public val JewelTheme.Companion.segmentedControlButtonStyle: SegmentedControlButtonStyle
    @Composable @ReadOnlyComposable get() = LocalSegmentedControlButtonStyle.current

/** The styling for segmented control components. */
public val JewelTheme.Companion.segmentedControlStyle: SegmentedControlStyle
    @Composable @ReadOnlyComposable get() = LocalSegmentedControlStyle.current

/** The styling for simple list item components. */
public val JewelTheme.Companion.simpleListItemStyle: SimpleListItemStyle
    @Composable @ReadOnlyComposable get() = LocalSimpleListItemStyleStyle.current

/** The styling for text area components. */
public val JewelTheme.Companion.textAreaStyle: TextAreaStyle
    @Composable @ReadOnlyComposable get() = LocalTextAreaStyle.current

/** The styling for text field components. */
public val JewelTheme.Companion.textFieldStyle: TextFieldStyle
    @Composable @ReadOnlyComposable get() = LocalTextFieldStyle.current

/** The styling for lazy tree components. */
public val JewelTheme.Companion.treeStyle: LazyTreeStyle
    @Composable @ReadOnlyComposable get() = LocalLazyTreeStyle.current

/** The styling for default tab components. */
public val JewelTheme.Companion.defaultTabStyle: TabStyle
    @Composable @ReadOnlyComposable get() = LocalDefaultTabStyle.current

/** The styling for editor tab components. */
public val JewelTheme.Companion.editorTabStyle: TabStyle
    @Composable @ReadOnlyComposable get() = LocalEditorTabStyle.current

/** The styling for circular progress indicator components. */
public val JewelTheme.Companion.circularProgressStyle: CircularProgressStyle
    @Composable @ReadOnlyComposable get() = LocalCircularProgressStyle.current

/** The styling for tooltip components. */
public val JewelTheme.Companion.tooltipStyle: TooltipStyle
    @Composable @ReadOnlyComposable get() = LocalTooltipStyle.current

/** The styling for icon button components. */
public val JewelTheme.Companion.iconButtonStyle: IconButtonStyle
    @Composable @ReadOnlyComposable get() = LocalIconButtonStyle.current

/** The styling for transparent icon button components. */
@get:ApiStatus.Experimental
@ExperimentalJewelApi
public val JewelTheme.Companion.transparentIconButtonStyle: IconButtonStyle
    @Composable @ReadOnlyComposable get() = LocalTransparentIconButtonStyle.current

/** The styling for slider components. */
public val JewelTheme.Companion.sliderStyle: SliderStyle
    @Composable @ReadOnlyComposable get() = LocalSliderStyle.current

/** The styling for speed search components. */
public val JewelTheme.Companion.speedSearchStyle: SpeedSearchStyle
    @Composable @ReadOnlyComposable get() = LocalSpeedSearchStyle.current

/** The styling for search match highlight components. */
public val JewelTheme.Companion.searchMatchStyle: SearchMatchStyle
    @Composable @ReadOnlyComposable get() = LocalSearchMatchStyle.current

/** The styling for popup ad components. */
public val JewelTheme.Companion.popupAdStyle: PopupAdStyle
    @Composable @ReadOnlyComposable get() = LocalPopupAdStyle.current

/** The styling for the default (filled) slim button. */
public val JewelTheme.Companion.defaultSlimButtonStyle: ButtonStyle
    @Composable @ReadOnlyComposable get() = LocalDefaultSlimButtonStyle.current

/** The styling for the outlined slim button. */
public val JewelTheme.Companion.outlinedSlimButtonStyle: ButtonStyle
    @Composable @ReadOnlyComposable get() = LocalOutlinedSlimButtonStyle.current

/** The styling for badge components. */
public val JewelTheme.Companion.badgeStyle: BadgeStyles
    @Composable @ReadOnlyComposable get() = LocalBadgeStyle.current

/**
 * Applies [theme] and [styling] to the [content] composition tree with Swing compat mode disabled.
 *
 * @param theme The [ThemeDefinition] describing colors, metrics, and text styles.
 * @param styling The [ComponentStyling] providing all component-level styles.
 * @param content The composable content to render under this theme.
 */
@Composable
public fun BaseJewelTheme(theme: ThemeDefinition, styling: ComponentStyling, content: @Composable () -> Unit) {
    BaseJewelTheme(theme, styling, swingCompatMode = false, content)
}

/**
 * Applies [theme] and [styling] to the [content] composition tree.
 *
 * @param theme The [ThemeDefinition] describing colors, metrics, and text styles.
 * @param styling The [ComponentStyling] providing all component-level styles.
 * @param swingCompatMode Whether to enable Swing compatibility mode (disables hover/press state changes).
 * @param content The composable content to render under this theme.
 */
@Composable
public fun BaseJewelTheme(
    theme: ThemeDefinition,
    styling: ComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    JewelTheme(theme, swingCompatMode) {
        CompositionLocalProvider(
            LocalColorPalette provides theme.colorPalette,
            LocalIconData provides theme.iconData,
            LocalIndication provides NoIndication,
        ) {
            CompositionLocalProvider(values = styling.styles(), content = content)
        }
    }
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node() {}

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun equals(other: Any?): Boolean = this === other
}
