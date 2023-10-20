package org.jetbrains.jewel

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.Stable
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
import org.jetbrains.jewel.styling.LocalCheckboxStyle
import org.jetbrains.jewel.styling.LocalChipStyle
import org.jetbrains.jewel.styling.LocalCircularProgressStyle
import org.jetbrains.jewel.styling.LocalDefaultButtonStyle
import org.jetbrains.jewel.styling.LocalDefaultDropdownStyle
import org.jetbrains.jewel.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.styling.LocalDividerStyle
import org.jetbrains.jewel.styling.LocalEditorTabStyle
import org.jetbrains.jewel.styling.LocalGroupHeaderStyle
import org.jetbrains.jewel.styling.LocalHorizontalProgressBarStyle
import org.jetbrains.jewel.styling.LocalIconButtonStyle
import org.jetbrains.jewel.styling.LocalLabelledTextFieldStyle
import org.jetbrains.jewel.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.styling.LocalLinkStyle
import org.jetbrains.jewel.styling.LocalMenuStyle
import org.jetbrains.jewel.styling.LocalOutlinedButtonStyle
import org.jetbrains.jewel.styling.LocalRadioButtonStyle
import org.jetbrains.jewel.styling.LocalScrollbarStyle
import org.jetbrains.jewel.styling.LocalTextAreaStyle
import org.jetbrains.jewel.styling.LocalTextFieldStyle
import org.jetbrains.jewel.styling.LocalTooltipStyle
import org.jetbrains.jewel.styling.LocalUndecoratedDropdownStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ScrollbarStyle
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.styling.TextAreaStyle
import org.jetbrains.jewel.styling.TextFieldStyle
import org.jetbrains.jewel.styling.TooltipStyle

@Stable
@GenerateDataFunctions
class IntelliJComponentStyling(
    val checkboxStyle: CheckboxStyle,
    val chipStyle: ChipStyle,
    val circularProgressStyle: CircularProgressStyle,
    val defaultButtonStyle: ButtonStyle,
    val defaultDropdownStyle: DropdownStyle,
    val defaultTabStyle: TabStyle,
    val dividerStyle: DividerStyle,
    val editorTabStyle: TabStyle,
    val groupHeaderStyle: GroupHeaderStyle,
    val horizontalProgressBarStyle: HorizontalProgressBarStyle,
    val iconButtonStyle: IconButtonStyle,
    val labelledTextFieldStyle: LabelledTextFieldStyle,
    val lazyTreeStyle: LazyTreeStyle,
    val linkStyle: LinkStyle,
    val menuStyle: MenuStyle,
    val outlinedButtonStyle: ButtonStyle,
    val radioButtonStyle: RadioButtonStyle,
    val scrollbarStyle: ScrollbarStyle,
    val textAreaStyle: TextAreaStyle,
    val textFieldStyle: TextFieldStyle,
    val tooltipStyle: TooltipStyle,
    val undecoratedDropdownStyle: DropdownStyle,
) {

    @Composable
    fun providedStyles(): Array<ProvidedValue<*>> = arrayOf(
        LocalCheckboxStyle provides checkboxStyle,
        LocalChipStyle provides chipStyle,
        LocalCircularProgressStyle provides circularProgressStyle,
        LocalContextMenuRepresentation provides IntelliJContextMenuRepresentation,
        LocalDefaultButtonStyle provides defaultButtonStyle,
        LocalDefaultDropdownStyle provides defaultDropdownStyle,
        LocalDefaultTabStyle provides defaultTabStyle,
        LocalDividerStyle provides dividerStyle,
        LocalEditorTabStyle provides editorTabStyle,
        LocalGroupHeaderStyle provides groupHeaderStyle,
        LocalHorizontalProgressBarStyle provides horizontalProgressBarStyle,
        LocalIconButtonStyle provides iconButtonStyle,
        LocalLabelledTextFieldStyle provides labelledTextFieldStyle,
        LocalLazyTreeStyle provides lazyTreeStyle,
        LocalLinkStyle provides linkStyle,
        LocalMenuStyle provides menuStyle,
        LocalOutlinedButtonStyle provides outlinedButtonStyle,
        LocalRadioButtonStyle provides radioButtonStyle,
        LocalScrollbarStyle provides scrollbarStyle,
        LocalTextAreaStyle provides textAreaStyle,
        LocalTextFieldStyle provides textFieldStyle,
        LocalTooltipStyle provides tooltipStyle,
        LocalUndecoratedDropdownStyle provides undecoratedDropdownStyle,
    )
}
