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
import org.jetbrains.jewel.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.styling.LocalDividerStyle
import org.jetbrains.jewel.styling.LocalDropdownStyle
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
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.styling.RadioButtonStyle
import org.jetbrains.jewel.styling.ScrollbarStyle
import org.jetbrains.jewel.styling.TabStyle
import org.jetbrains.jewel.styling.TextAreaStyle
import org.jetbrains.jewel.styling.TextFieldStyle
import org.jetbrains.jewel.styling.TooltipStyle

@Stable
class IntelliJComponentStyling(
    val checkboxStyle: CheckboxStyle,
    val chipStyle: ChipStyle,
    val defaultButtonStyle: ButtonStyle,
    val defaultTabStyle: TabStyle,
    val dividerStyle: DividerStyle,
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
    val tooltipStyle: TooltipStyle,
    val iconButtonStyle: IconButtonStyle,
) {

    @Composable
    fun providedStyles(): Array<ProvidedValue<*>> = arrayOf(
        LocalCheckboxStyle provides checkboxStyle,
        LocalChipStyle provides chipStyle,
        LocalContextMenuRepresentation provides IntelliJContextMenuRepresentation,
        LocalDefaultButtonStyle provides defaultButtonStyle,
        LocalDividerStyle provides dividerStyle,
        LocalDropdownStyle provides dropdownStyle,
        LocalGroupHeaderStyle provides groupHeaderStyle,
        LocalHorizontalProgressBarStyle provides horizontalProgressBarStyle,
        LocalLabelledTextFieldStyle provides labelledTextFieldStyle,
        LocalLazyTreeStyle provides lazyTreeStyle,
        LocalLinkStyle provides linkStyle,
        LocalMenuStyle provides menuStyle,
        LocalOutlinedButtonStyle provides outlinedButtonStyle,
        LocalRadioButtonStyle provides radioButtonStyle,
        LocalScrollbarStyle provides scrollbarStyle,
        LocalTextAreaStyle provides textAreaStyle,
        LocalTextFieldStyle provides textFieldStyle,
        LocalDefaultTabStyle provides defaultTabStyle,
        LocalEditorTabStyle provides editorTabStyle,
        LocalCircularProgressStyle provides circularProgressStyle,
        LocalTooltipStyle provides tooltipStyle,
        LocalIconButtonStyle provides iconButtonStyle,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntelliJComponentStyling

        if (checkboxStyle != other.checkboxStyle) return false
        if (chipStyle != other.chipStyle) return false
        if (defaultButtonStyle != other.defaultButtonStyle) return false
        if (defaultTabStyle != other.defaultTabStyle) return false
        if (dividerStyle != other.dividerStyle) return false
        if (dropdownStyle != other.dropdownStyle) return false
        if (editorTabStyle != other.editorTabStyle) return false
        if (groupHeaderStyle != other.groupHeaderStyle) return false
        if (horizontalProgressBarStyle != other.horizontalProgressBarStyle) return false
        if (labelledTextFieldStyle != other.labelledTextFieldStyle) return false
        if (lazyTreeStyle != other.lazyTreeStyle) return false
        if (linkStyle != other.linkStyle) return false
        if (menuStyle != other.menuStyle) return false
        if (outlinedButtonStyle != other.outlinedButtonStyle) return false
        if (radioButtonStyle != other.radioButtonStyle) return false
        if (scrollbarStyle != other.scrollbarStyle) return false
        if (textAreaStyle != other.textAreaStyle) return false
        if (textFieldStyle != other.textFieldStyle) return false
        if (circularProgressStyle != other.circularProgressStyle) return false
        if (tooltipStyle != other.tooltipStyle) return false
        if (iconButtonStyle != other.iconButtonStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = checkboxStyle.hashCode()
        result = 31 * result + chipStyle.hashCode()
        result = 31 * result + defaultButtonStyle.hashCode()
        result = 31 * result + defaultTabStyle.hashCode()
        result = 31 * result + dividerStyle.hashCode()
        result = 31 * result + dropdownStyle.hashCode()
        result = 31 * result + editorTabStyle.hashCode()
        result = 31 * result + groupHeaderStyle.hashCode()
        result = 31 * result + horizontalProgressBarStyle.hashCode()
        result = 31 * result + labelledTextFieldStyle.hashCode()
        result = 31 * result + lazyTreeStyle.hashCode()
        result = 31 * result + linkStyle.hashCode()
        result = 31 * result + menuStyle.hashCode()
        result = 31 * result + outlinedButtonStyle.hashCode()
        result = 31 * result + radioButtonStyle.hashCode()
        result = 31 * result + scrollbarStyle.hashCode()
        result = 31 * result + textAreaStyle.hashCode()
        result = 31 * result + textFieldStyle.hashCode()
        result = 31 * result + circularProgressStyle.hashCode()
        result = 31 * result + tooltipStyle.hashCode()
        result = 31 * result + iconButtonStyle.hashCode()
        return result
    }

    override fun toString() =
        "IntelliJComponentStyling(checkboxStyle=$checkboxStyle, chipStyle=$chipStyle, " +
            "defaultButtonStyle=$defaultButtonStyle, defaultTabStyle=$defaultTabStyle, " +
            "dividerStyle=$dividerStyle, dropdownStyle=$dropdownStyle, editorTabStyle=$editorTabStyle, " +
            "groupHeaderStyle=$groupHeaderStyle, horizontalProgressBarStyle=$horizontalProgressBarStyle, " +
            "labelledTextFieldStyle=$labelledTextFieldStyle, lazyTreeStyle=$lazyTreeStyle, linkStyle=$linkStyle, " +
            "menuStyle=$menuStyle, outlinedButtonStyle=$outlinedButtonStyle, radioButtonStyle=$radioButtonStyle, " +
            "scrollbarStyle=$scrollbarStyle, textAreaStyle=$textAreaStyle, textFieldStyle=$textFieldStyle, " +
            "circularProgressStyle=$circularProgressStyle, tooltipStyle=$tooltipStyle, iconButtonStyle=$iconButtonStyle)"
}
