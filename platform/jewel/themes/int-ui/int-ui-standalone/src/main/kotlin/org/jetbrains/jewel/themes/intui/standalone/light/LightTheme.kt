package org.jetbrains.jewel.themes.intui.standalone.light

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

object LightTheme : IntUiTheme(LightPalette) {

    override val colors: IntelliJColors = IntelliJColors(
        foreground = palette.grey(1),
        background = palette.grey(13),
        borderColor = palette.grey(12),

        disabledForeground = palette.grey(8),
        disabledBackground = palette.grey(14),
        disabledBorderColor = palette.grey(11)
    )

    override val buttonDefaults: ButtonDefaults = LightButtonDefaults
    override val checkboxDefaults: CheckboxDefaults = LightCheckboxDefaults
    override val groupHeaderDefaults: GroupHeaderDefaults = LightGroupHeaderDefaults
    override val linkDefaults: LinkDefaults = LightLinkDefaults
    override val textFieldDefaults: TextFieldDefaults = LightTextFieldDefaults
    override val labelledTextFieldDefaults: LabelledTextFieldDefaults = LightLabelledTextFieldDefaults
    override val textAreaDefaults: TextAreaDefaults = LightTextAreaDefaults
    override val radioButtonDefaults: RadioButtonDefaults = LightRadioButtonDefaults
    override val dropdownDefaults: DropdownDefaults = LightDropdownDefaults
    override val contextMenuDefaults: MenuDefaults = LightMenuDefaults
    override val treeDefaults: TreeDefaults = LightTreeDefaults
    override val chipDefaults: ChipDefaults = LightChipDefaults
    override val scrollThumbDefaults: ScrollThumbDefaults = LightScrollThumbDefaults
    override val progressBarDefaults: ProgressBarDefaults = LightProgressBarDefaults

    override val isLight: Boolean = true
}
