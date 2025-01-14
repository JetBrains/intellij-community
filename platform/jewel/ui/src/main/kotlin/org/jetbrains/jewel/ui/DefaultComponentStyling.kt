package org.jetbrains.jewel.ui

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.Stable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ContextMenuRepresentation
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
}
