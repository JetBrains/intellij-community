package org.jetbrains.jewel

import androidx.compose.runtime.Stable
import org.jetbrains.jewel.styling.ButtonStyle
import org.jetbrains.jewel.styling.CheckboxStyle
import org.jetbrains.jewel.styling.ChipStyle
import org.jetbrains.jewel.styling.CircularProgressStyle
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.GroupHeaderStyle
import org.jetbrains.jewel.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.styling.LabelledTextFieldStyle
import org.jetbrains.jewel.styling.LazyTreeStyle
import org.jetbrains.jewel.styling.LinkStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ScrollbarStyle
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.styling.TextAreaStyle
import org.jetbrains.jewel.styling.TextFieldStyle

@Stable
class IntelliJComponentStyling(
    val checkboxStyle: CheckboxStyle,
    val chipStyle: ChipStyle,
    val defaultButtonStyle: ButtonStyle,
    val defaultTabStyle: TabStyle,
    val dropdownStyle: DropdownStyle,
    val editorTabStyle: TabStyle,
    val groupHeaderStyle: GroupHeaderStyle,
    val horizontalProgressBarStyle: HorizontalProgressBarStyle,
    val labelledTextFieldStyle: LabelledTextFieldStyle,
    val lazyTreeStyle: LazyTreeStyle,
    val linkStyle: LinkStyle,
    val menuStyle: MenuStyle,
    val outlinedButtonStyle: ButtonStyle,
    val radioButtonStyle: RadioButtonStyle,
    val scrollbarStyle: ScrollbarStyle,
    val textAreaStyle: TextAreaStyle,
    val textFieldStyle: TextFieldStyle,
    val circularProgressStyle: CircularProgressStyle,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntelliJComponentStyling

        if (defaultButtonStyle != other.defaultButtonStyle) return false
        if (outlinedButtonStyle != other.outlinedButtonStyle) return false
        if (checkboxStyle != other.checkboxStyle) return false
        if (chipStyle != other.chipStyle) return false
        if (dropdownStyle != other.dropdownStyle) return false
        if (groupHeaderStyle != other.groupHeaderStyle) return false
        if (labelledTextFieldStyle != other.labelledTextFieldStyle) return false
        if (linkStyle != other.linkStyle) return false
        if (menuStyle != other.menuStyle) return false
        if (horizontalProgressBarStyle != other.horizontalProgressBarStyle) return false
        if (radioButtonStyle != other.radioButtonStyle) return false
        if (scrollbarStyle != other.scrollbarStyle) return false
        if (textAreaStyle != other.textAreaStyle) return false
        if (textFieldStyle != other.textFieldStyle) return false
        if (lazyTreeStyle != other.lazyTreeStyle) return false
        if (defaultTabStyle != other.defaultTabStyle) return false
        if (editorTabStyle != other.editorTabStyle) return false
        if (circularProgressStyle != other.circularProgressStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = defaultButtonStyle.hashCode()
        result = 31 * result + outlinedButtonStyle.hashCode()
        result = 31 * result + checkboxStyle.hashCode()
        result = 31 * result + chipStyle.hashCode()
        result = 31 * result + dropdownStyle.hashCode()
        result = 31 * result + groupHeaderStyle.hashCode()
        result = 31 * result + labelledTextFieldStyle.hashCode()
        result = 31 * result + linkStyle.hashCode()
        result = 31 * result + menuStyle.hashCode()
        result = 31 * result + horizontalProgressBarStyle.hashCode()
        result = 31 * result + radioButtonStyle.hashCode()
        result = 31 * result + scrollbarStyle.hashCode()
        result = 31 * result + textAreaStyle.hashCode()
        result = 31 * result + textFieldStyle.hashCode()
        result = 31 * result + lazyTreeStyle.hashCode()
        result = 31 * result + defaultTabStyle.hashCode()
        result = 31 * result + editorTabStyle.hashCode()
        result = 31 * result + circularProgressStyle.hashCode()
        return result
    }

    override fun toString(): String =
        "IntelliJComponentStyling(checkboxStyle=$checkboxStyle, chipStyle=$chipStyle, " +
            "defaultButtonStyle=$defaultButtonStyle, defaultTabStyle=$defaultTabStyle, dropdownStyle=$dropdownStyle, " +
            "editorTabStyle=$editorTabStyle, groupHeaderStyle=$groupHeaderStyle, " +
            "horizontalProgressBarStyle=$horizontalProgressBarStyle, labelledTextFieldStyle=$labelledTextFieldStyle, " +
            "lazyTreeStyle=$lazyTreeStyle, linkStyle=$linkStyle, menuStyle=$menuStyle, " +
            "outlinedButtonStyle=$outlinedButtonStyle, radioButtonStyle=$radioButtonStyle, " +
            "scrollbarStyle=$scrollbarStyle, textAreaStyle=$textAreaStyle, textFieldStyle=$textFieldStyle" +
            "circularProgressStyle=$circularProgressStyle)"
}
