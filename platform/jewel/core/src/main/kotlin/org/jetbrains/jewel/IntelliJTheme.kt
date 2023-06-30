package org.jetbrains.jewel

import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

@ExperimentalJewelApi
interface IntelliJTheme {

    val colors: IntelliJColors

    val buttonDefaults: ButtonDefaults

    val checkboxDefaults: CheckboxDefaults

    val groupHeaderDefaults: GroupHeaderDefaults

    val linkDefaults: LinkDefaults

    val textFieldDefaults: TextFieldDefaults

    val labelledTextFieldDefaults: LabelledTextFieldDefaults

    val textAreaDefaults: TextAreaDefaults

    val radioButtonDefaults: RadioButtonDefaults

    val dropdownDefaults: DropdownDefaults

    val contextMenuDefaults: MenuDefaults

    val defaultTextStyle: TextStyle

    val treeDefaults: TreeDefaults

    val chipDefaults: ChipDefaults

    val scrollThumbDefaults: ScrollThumbDefaults

    val progressBarDefaults: ProgressBarDefaults

    val isLight: Boolean

    fun providedCompositionLocalValues(): Array<ProvidedValue<*>>

    companion object {

        val buttonDefaults: ButtonDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalButtonDefaults.current

        val checkboxDefaults: CheckboxDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalCheckboxDefaults.current

        val groupHeaderDefaults: GroupHeaderDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalGroupHeaderDefaults.current

        val linkDefaults: LinkDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalLinkDefaults.current

        val textFieldDefaults: TextFieldDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalTextFieldDefaults.current

        val labelledTextFieldDefaults: LabelledTextFieldDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalLabelledTextFieldDefaults.current

        val textAreaDefaults: TextAreaDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalTextAreaDefaults.current

        val radioButtonDefaults: RadioButtonDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalRadioButtonDefaults.current

        val treeDefaults: TreeDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalTreeDefaults.current

        val chipDefaults: ChipDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalChipDefaults.current

        val dropdownDefaults: DropdownDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalDropdownDefaults.current

        val contextMenuDefaults: MenuDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalContextMenuDefaults.current

        val menuDefaults: MenuDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalMenuDefaults.current

        val colors: IntelliJColors
            @Composable
            @ReadOnlyComposable
            get() = LocalIntelliJColors.current

        val defaultTextStyle: TextStyle
            @Composable
            @ReadOnlyComposable
            get() = LocalTextStyle.current

        val scrollThumbDefaults: ScrollThumbDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalScrollThumbDefaults.current

        val progressBarDefaults: ProgressBarDefaults
            @Composable
            @ReadOnlyComposable
            get() = LocalProgressBarDefaults.current

        val isLight: Boolean
            @Composable
            @ReadOnlyComposable
            get() = LocalInLightTheme.current
    }
}

@ExperimentalJewelApi
@Composable
fun IntelliJTheme(theme: IntelliJTheme, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIntelliJColors provides theme.colors,
        LocalButtonDefaults provides theme.buttonDefaults,
        LocalCheckboxDefaults provides theme.checkboxDefaults,
        LocalGroupHeaderDefaults provides theme.groupHeaderDefaults,
        LocalLinkDefaults provides theme.linkDefaults,
        LocalTextFieldDefaults provides theme.textFieldDefaults,
        LocalLabelledTextFieldDefaults provides theme.labelledTextFieldDefaults,
        LocalTextAreaDefaults provides theme.textAreaDefaults,
        LocalRadioButtonDefaults provides theme.radioButtonDefaults,
        LocalDropdownDefaults provides theme.dropdownDefaults,
        LocalContextMenuDefaults provides theme.contextMenuDefaults,
        LocalTreeDefaults provides theme.treeDefaults,
        LocalChipDefaults provides theme.chipDefaults,
        LocalScrollThumbDefaults provides theme.scrollThumbDefaults,
        LocalProgressBarDefaults provides theme.progressBarDefaults,
        LocalTextStyle provides theme.defaultTextStyle,
        LocalTextColor provides theme.colors.foreground,
        LocalInLightTheme provides theme.isLight,
        LocalContextMenuRepresentation provides IntelliJContextMenuRepresentation,
        *theme.providedCompositionLocalValues(),
        content = content
    )
}

internal val LocalInLightTheme = staticCompositionLocalOf<Boolean> {
    error("No IntelliJTheme provided")
}
