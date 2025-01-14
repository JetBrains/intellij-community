package org.jetbrains.jewel.ui

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.Stable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ContextMenuRepresentation
import org.jetbrains.jewel.ui.component.styling.*

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
