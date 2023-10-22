package org.jetbrains.jewel.ui

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.Stable
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ContextMenuRepresentation
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalEditorTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextAreaStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextFieldStyle
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

@Stable
@GenerateDataFunctions
class ComponentStyling(
    val checkboxStyle: org.jetbrains.jewel.ui.component.styling.CheckboxStyle,
    val chipStyle: org.jetbrains.jewel.ui.component.styling.ChipStyle,
    val circularProgressStyle: org.jetbrains.jewel.ui.component.styling.CircularProgressStyle,
    val defaultButtonStyle: org.jetbrains.jewel.ui.component.styling.ButtonStyle,
    val defaultDropdownStyle: org.jetbrains.jewel.ui.component.styling.DropdownStyle,
    val defaultTabStyle: TabStyle,
    val dividerStyle: org.jetbrains.jewel.ui.component.styling.DividerStyle,
    val editorTabStyle: TabStyle,
    val groupHeaderStyle: org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle,
    val horizontalProgressBarStyle: org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle,
    val iconButtonStyle: org.jetbrains.jewel.ui.component.styling.IconButtonStyle,
    val labelledTextFieldStyle: org.jetbrains.jewel.ui.component.styling.LabelledTextFieldStyle,
    val lazyTreeStyle: org.jetbrains.jewel.ui.component.styling.LazyTreeStyle,
    val linkStyle: org.jetbrains.jewel.ui.component.styling.LinkStyle,
    val menuStyle: org.jetbrains.jewel.ui.component.styling.MenuStyle,
    val outlinedButtonStyle: org.jetbrains.jewel.ui.component.styling.ButtonStyle,
    val radioButtonStyle: org.jetbrains.jewel.ui.component.styling.RadioButtonStyle,
    val scrollbarStyle: org.jetbrains.jewel.ui.component.styling.ScrollbarStyle,
    val textAreaStyle: TextAreaStyle,
    val textFieldStyle: TextFieldStyle,
    val tooltipStyle: TooltipStyle,
    val undecoratedDropdownStyle: org.jetbrains.jewel.ui.component.styling.DropdownStyle,
) {

    @Composable
    fun providedStyles(): Array<ProvidedValue<*>> = arrayOf(
        org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle provides checkboxStyle,
        org.jetbrains.jewel.ui.component.styling.LocalChipStyle provides chipStyle,
        org.jetbrains.jewel.ui.component.styling.LocalCircularProgressStyle provides circularProgressStyle,
        LocalContextMenuRepresentation provides ContextMenuRepresentation,
        org.jetbrains.jewel.ui.component.styling.LocalDefaultButtonStyle provides defaultButtonStyle,
        org.jetbrains.jewel.ui.component.styling.LocalDefaultDropdownStyle provides defaultDropdownStyle,
        LocalDefaultTabStyle provides defaultTabStyle,
        org.jetbrains.jewel.ui.component.styling.LocalDividerStyle provides dividerStyle,
        LocalEditorTabStyle provides editorTabStyle,
        org.jetbrains.jewel.ui.component.styling.LocalGroupHeaderStyle provides groupHeaderStyle,
        org.jetbrains.jewel.ui.component.styling.LocalHorizontalProgressBarStyle provides horizontalProgressBarStyle,
        org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle provides iconButtonStyle,
        org.jetbrains.jewel.ui.component.styling.LocalLabelledTextFieldStyle provides labelledTextFieldStyle,
        org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle provides lazyTreeStyle,
        org.jetbrains.jewel.ui.component.styling.LocalLinkStyle provides linkStyle,
        org.jetbrains.jewel.ui.component.styling.LocalMenuStyle provides menuStyle,
        org.jetbrains.jewel.ui.component.styling.LocalOutlinedButtonStyle provides outlinedButtonStyle,
        org.jetbrains.jewel.ui.component.styling.LocalRadioButtonStyle provides radioButtonStyle,
        org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle provides scrollbarStyle,
        LocalTextAreaStyle provides textAreaStyle,
        LocalTextFieldStyle provides textFieldStyle,
        LocalTooltipStyle provides tooltipStyle,
        org.jetbrains.jewel.ui.component.styling.LocalUndecoratedDropdownStyle provides undecoratedDropdownStyle,
    )
}
