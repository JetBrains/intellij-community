package org.jetbrains.jewel.bridge

import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.ButtonDefaults
import org.jetbrains.jewel.CheckboxDefaults
import org.jetbrains.jewel.ChipDefaults
import org.jetbrains.jewel.DropdownDefaults
import org.jetbrains.jewel.GroupHeaderDefaults
import org.jetbrains.jewel.IntelliJColors
import org.jetbrains.jewel.LabelledTextFieldDefaults
import org.jetbrains.jewel.LinkDefaults
import org.jetbrains.jewel.MenuDefaults
import org.jetbrains.jewel.ThemeColors
import org.jetbrains.jewel.ProgressBarDefaults
import org.jetbrains.jewel.RadioButtonDefaults
import org.jetbrains.jewel.ScrollThumbDefaults
import org.jetbrains.jewel.TextAreaDefaults
import org.jetbrains.jewel.TextFieldDefaults
import org.jetbrains.jewel.TreeDefaults
import org.jetbrains.jewel.themes.intui.core.IntUiColorPalette
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

class IntUiBridgeTheme internal constructor(
    override val isDark: Boolean,
    palette: IntUiColorPalette,
    override val colors: IntelliJColors,
    val themeColors: ThemeColors,
    override val buttonDefaults: ButtonDefaults,
    override val checkboxDefaults: CheckboxDefaults,
    override val groupHeaderDefaults: GroupHeaderDefaults,
    override val linkDefaults: LinkDefaults,
    override val textFieldDefaults: TextFieldDefaults,
    override val labelledTextFieldDefaults: LabelledTextFieldDefaults,
    override val textAreaDefaults: TextAreaDefaults,
    override val radioButtonDefaults: RadioButtonDefaults,
    override val dropdownDefaults: DropdownDefaults,
    override val contextMenuDefaults: MenuDefaults,
    override val defaultTextStyle: TextStyle,
    override val treeDefaults: TreeDefaults,
    override val chipDefaults: ChipDefaults,
    override val scrollThumbDefaults: ScrollThumbDefaults,
    override val progressBarDefaults: ProgressBarDefaults,
): IntUiTheme(palette)
