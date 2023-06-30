package org.jetbrains.jewel.themes.intui.standalone.dark

import org.jetbrains.jewel.ButtonDefaults
import org.jetbrains.jewel.CheckboxDefaults
import org.jetbrains.jewel.ChipDefaults
import org.jetbrains.jewel.DropdownDefaults
import org.jetbrains.jewel.GroupHeaderDefaults
import org.jetbrains.jewel.IntelliJColors
import org.jetbrains.jewel.LabelledTextFieldDefaults
import org.jetbrains.jewel.LinkDefaults
import org.jetbrains.jewel.MenuDefaults
import org.jetbrains.jewel.ProgressBarDefaults
import org.jetbrains.jewel.RadioButtonDefaults
import org.jetbrains.jewel.ScrollThumbDefaults
import org.jetbrains.jewel.TextAreaDefaults
import org.jetbrains.jewel.TextFieldDefaults
import org.jetbrains.jewel.TreeDefaults
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

object DarkTheme : IntUiTheme(DarkPalette) {

    override val colors: IntelliJColors = IntelliJColors(
        foreground = palette.grey(12),
        background = palette.grey(2),
        borderColor = palette.grey(1),

        disabledForeground = palette.grey(5),
        disabledBackground = palette.grey(1),
        disabledBorderColor = palette.grey(3)
    )

    override val buttonDefaults: ButtonDefaults = DarkButtonDefaults
    override val checkboxDefaults: CheckboxDefaults = DarkCheckboxDefaults
    override val groupHeaderDefaults: GroupHeaderDefaults = DarkGroupHeaderDefaults
    override val linkDefaults: LinkDefaults = DarkLinkDefaults
    override val textFieldDefaults: TextFieldDefaults = DarkTextFieldDefaults
    override val labelledTextFieldDefaults: LabelledTextFieldDefaults = DarkLabelledTextFieldDefaults
    override val textAreaDefaults: TextAreaDefaults = DarkTextAreaDefaults
    override val radioButtonDefaults: RadioButtonDefaults = DarkRadioButtonDefaults
    override val dropdownDefaults: DropdownDefaults = DarkDropdownDefaults
    override val contextMenuDefaults: MenuDefaults = DarkMenuDefaults
    override val treeDefaults: TreeDefaults = DarkTreeDefaults
    override val chipDefaults: ChipDefaults = DarkChipDefaults
    override val scrollThumbDefaults: ScrollThumbDefaults = DarkScrollThumbDefaults
    override val progressBarDefaults: ProgressBarDefaults = DarkProgressBarDefaults

    override val isLight: Boolean = false
}
