package org.jetbrains.jewel.ui

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.Stable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ContextMenuRepresentation
import org.jetbrains.jewel.ui.component.TextContextMenu
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
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.component.styling.LocalChipStyle
import org.jetbrains.jewel.ui.component.styling.LocalCircularProgressStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultBannerStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultComboBoxStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultDropdownStyle
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
import org.jetbrains.jewel.ui.component.styling.LocalOutlinedSplitButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalPopupContainerStyle
import org.jetbrains.jewel.ui.component.styling.LocalRadioButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.LocalSegmentedControlButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalSegmentedControlStyle
import org.jetbrains.jewel.ui.component.styling.LocalSelectableLazyColumnStyle
import org.jetbrains.jewel.ui.component.styling.LocalSimpleListItemStyleStyle
import org.jetbrains.jewel.ui.component.styling.LocalSliderStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextAreaStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextFieldStyle
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle
import org.jetbrains.jewel.ui.component.styling.LocalTransparentIconButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalUndecoratedDropdownStyle
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
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

@Stable
@GenerateDataFunctions
public class DefaultComponentStyling(
    public val checkboxStyle: CheckboxStyle,
    public val chipStyle: ChipStyle,
    public val circularProgressStyle: CircularProgressStyle,
    public val defaultBannerStyle: DefaultBannerStyles,
    public val comboBoxStyle: ComboBoxStyle,
    public val defaultButtonStyle: ButtonStyle,
    public val defaultDropdownStyle: DropdownStyle,
    public val defaultSplitButtonStyle: SplitButtonStyle,
    public val defaultTabStyle: TabStyle,
    public val dividerStyle: DividerStyle,
    public val editorTabStyle: TabStyle,
    public val groupHeaderStyle: GroupHeaderStyle,
    public val horizontalProgressBarStyle: HorizontalProgressBarStyle,
    public val iconButtonStyle: IconButtonStyle,
    public val transparentIconButtonStyle: IconButtonStyle,
    public val inlineBannerStyle: InlineBannerStyles,
    public val lazyTreeStyle: LazyTreeStyle,
    public val linkStyle: LinkStyle,
    public val menuStyle: MenuStyle,
    public val outlinedButtonStyle: ButtonStyle,
    public val popupContainerStyle: PopupContainerStyle,
    public val outlinedSplitButtonStyle: SplitButtonStyle,
    public val radioButtonStyle: RadioButtonStyle,
    public val scrollbarStyle: ScrollbarStyle,
    public val segmentedControlButtonStyle: SegmentedControlButtonStyle,
    public val segmentedControlStyle: SegmentedControlStyle,
    public val selectableLazyColumnStyle: SelectableLazyColumnStyle,
    public val simpleListItemStyle: SimpleListItemStyle,
    public val sliderStyle: SliderStyle,
    public val textAreaStyle: TextAreaStyle,
    public val textFieldStyle: TextFieldStyle,
    public val tooltipStyle: TooltipStyle,
    public val undecoratedDropdownStyle: DropdownStyle,
) : ComponentStyling {
    @Composable
    override fun styles(): Array<out ProvidedValue<*>> =
        arrayOf(
            LocalCheckboxStyle provides checkboxStyle,
            LocalChipStyle provides chipStyle,
            LocalCircularProgressStyle provides circularProgressStyle,
            LocalContextMenuRepresentation provides ContextMenuRepresentation,
            LocalTextContextMenu provides TextContextMenu,
            LocalDefaultBannerStyle provides defaultBannerStyle,
            LocalDefaultComboBoxStyle provides comboBoxStyle,
            LocalDefaultButtonStyle provides defaultButtonStyle,
            LocalDefaultSplitButtonStyle provides defaultSplitButtonStyle,
            LocalDefaultDropdownStyle provides defaultDropdownStyle,
            LocalDefaultTabStyle provides defaultTabStyle,
            LocalDividerStyle provides dividerStyle,
            LocalEditorTabStyle provides editorTabStyle,
            LocalGroupHeaderStyle provides groupHeaderStyle,
            LocalHorizontalProgressBarStyle provides horizontalProgressBarStyle,
            LocalIconButtonStyle provides iconButtonStyle,
            LocalTransparentIconButtonStyle provides transparentIconButtonStyle,
            LocalInlineBannerStyle provides inlineBannerStyle,
            LocalLazyTreeStyle provides lazyTreeStyle,
            LocalLinkStyle provides linkStyle,
            LocalMenuStyle provides menuStyle,
            LocalOutlinedButtonStyle provides outlinedButtonStyle,
            LocalPopupContainerStyle provides popupContainerStyle,
            LocalOutlinedSplitButtonStyle provides outlinedSplitButtonStyle,
            LocalRadioButtonStyle provides radioButtonStyle,
            LocalScrollbarStyle provides scrollbarStyle,
            LocalSegmentedControlButtonStyle provides segmentedControlButtonStyle,
            LocalSegmentedControlStyle provides segmentedControlStyle,
            LocalSelectableLazyColumnStyle provides selectableLazyColumnStyle,
            LocalSimpleListItemStyleStyle provides simpleListItemStyle,
            LocalSliderStyle provides sliderStyle,
            LocalTextAreaStyle provides textAreaStyle,
            LocalTextFieldStyle provides textFieldStyle,
            LocalTooltipStyle provides tooltipStyle,
            LocalUndecoratedDropdownStyle provides undecoratedDropdownStyle,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultComponentStyling

        if (checkboxStyle != other.checkboxStyle) return false
        if (chipStyle != other.chipStyle) return false
        if (circularProgressStyle != other.circularProgressStyle) return false
        if (defaultBannerStyle != other.defaultBannerStyle) return false
        if (comboBoxStyle != other.comboBoxStyle) return false
        if (defaultButtonStyle != other.defaultButtonStyle) return false
        if (defaultDropdownStyle != other.defaultDropdownStyle) return false
        if (defaultSplitButtonStyle != other.defaultSplitButtonStyle) return false
        if (defaultTabStyle != other.defaultTabStyle) return false
        if (dividerStyle != other.dividerStyle) return false
        if (editorTabStyle != other.editorTabStyle) return false
        if (groupHeaderStyle != other.groupHeaderStyle) return false
        if (horizontalProgressBarStyle != other.horizontalProgressBarStyle) return false
        if (iconButtonStyle != other.iconButtonStyle) return false
        if (transparentIconButtonStyle != other.transparentIconButtonStyle) return false
        if (inlineBannerStyle != other.inlineBannerStyle) return false
        if (lazyTreeStyle != other.lazyTreeStyle) return false
        if (linkStyle != other.linkStyle) return false
        if (menuStyle != other.menuStyle) return false
        if (outlinedButtonStyle != other.outlinedButtonStyle) return false
        if (popupContainerStyle != other.popupContainerStyle) return false
        if (outlinedSplitButtonStyle != other.outlinedSplitButtonStyle) return false
        if (radioButtonStyle != other.radioButtonStyle) return false
        if (scrollbarStyle != other.scrollbarStyle) return false
        if (segmentedControlButtonStyle != other.segmentedControlButtonStyle) return false
        if (segmentedControlStyle != other.segmentedControlStyle) return false
        if (selectableLazyColumnStyle != other.selectableLazyColumnStyle) return false
        if (simpleListItemStyle != other.simpleListItemStyle) return false
        if (sliderStyle != other.sliderStyle) return false
        if (textAreaStyle != other.textAreaStyle) return false
        if (textFieldStyle != other.textFieldStyle) return false
        if (tooltipStyle != other.tooltipStyle) return false
        if (undecoratedDropdownStyle != other.undecoratedDropdownStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = checkboxStyle.hashCode()
        result = 31 * result + chipStyle.hashCode()
        result = 31 * result + circularProgressStyle.hashCode()
        result = 31 * result + defaultBannerStyle.hashCode()
        result = 31 * result + comboBoxStyle.hashCode()
        result = 31 * result + defaultButtonStyle.hashCode()
        result = 31 * result + defaultDropdownStyle.hashCode()
        result = 31 * result + defaultSplitButtonStyle.hashCode()
        result = 31 * result + defaultTabStyle.hashCode()
        result = 31 * result + dividerStyle.hashCode()
        result = 31 * result + editorTabStyle.hashCode()
        result = 31 * result + groupHeaderStyle.hashCode()
        result = 31 * result + horizontalProgressBarStyle.hashCode()
        result = 31 * result + iconButtonStyle.hashCode()
        result = 31 * result + transparentIconButtonStyle.hashCode()
        result = 31 * result + inlineBannerStyle.hashCode()
        result = 31 * result + lazyTreeStyle.hashCode()
        result = 31 * result + linkStyle.hashCode()
        result = 31 * result + menuStyle.hashCode()
        result = 31 * result + outlinedButtonStyle.hashCode()
        result = 31 * result + popupContainerStyle.hashCode()
        result = 31 * result + outlinedSplitButtonStyle.hashCode()
        result = 31 * result + radioButtonStyle.hashCode()
        result = 31 * result + scrollbarStyle.hashCode()
        result = 31 * result + segmentedControlButtonStyle.hashCode()
        result = 31 * result + segmentedControlStyle.hashCode()
        result = 31 * result + selectableLazyColumnStyle.hashCode()
        result = 31 * result + simpleListItemStyle.hashCode()
        result = 31 * result + sliderStyle.hashCode()
        result = 31 * result + textAreaStyle.hashCode()
        result = 31 * result + textFieldStyle.hashCode()
        result = 31 * result + tooltipStyle.hashCode()
        result = 31 * result + undecoratedDropdownStyle.hashCode()
        return result
    }

    override fun toString(): String {
        return "DefaultComponentStyling(" +
            "checkboxStyle=$checkboxStyle, " +
            "chipStyle=$chipStyle, " +
            "circularProgressStyle=$circularProgressStyle, " +
            "defaultBannerStyle=$defaultBannerStyle, " +
            "comboBoxStyle=$comboBoxStyle, " +
            "defaultButtonStyle=$defaultButtonStyle, " +
            "defaultDropdownStyle=$defaultDropdownStyle, " +
            "defaultSplitButtonStyle=$defaultSplitButtonStyle, " +
            "defaultTabStyle=$defaultTabStyle, " +
            "dividerStyle=$dividerStyle, " +
            "editorTabStyle=$editorTabStyle, " +
            "groupHeaderStyle=$groupHeaderStyle, " +
            "horizontalProgressBarStyle=$horizontalProgressBarStyle, " +
            "iconButtonStyle=$iconButtonStyle, " +
            "transparentIconButtonStyle=$transparentIconButtonStyle, " +
            "inlineBannerStyle=$inlineBannerStyle, " +
            "lazyTreeStyle=$lazyTreeStyle, " +
            "linkStyle=$linkStyle, " +
            "menuStyle=$menuStyle, " +
            "outlinedButtonStyle=$outlinedButtonStyle, " +
            "popupContainerStyle=$popupContainerStyle, " +
            "outlinedSplitButtonStyle=$outlinedSplitButtonStyle, " +
            "radioButtonStyle=$radioButtonStyle, " +
            "scrollbarStyle=$scrollbarStyle, " +
            "segmentedControlButtonStyle=$segmentedControlButtonStyle, " +
            "segmentedControlStyle=$segmentedControlStyle, " +
            "selectableLazyColumnStyle=$selectableLazyColumnStyle, " +
            "simpleListItemStyle=$simpleListItemStyle, " +
            "sliderStyle=$sliderStyle, " +
            "textAreaStyle=$textAreaStyle, " +
            "textFieldStyle=$textFieldStyle, " +
            "tooltipStyle=$tooltipStyle, " +
            "undecoratedDropdownStyle=$undecoratedDropdownStyle" +
            ")"
    }
}
