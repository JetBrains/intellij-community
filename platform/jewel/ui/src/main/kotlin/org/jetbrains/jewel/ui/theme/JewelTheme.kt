package org.jetbrains.jewel.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalColorPalette
import org.jetbrains.jewel.foundation.theme.LocalIconData
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.NoIndication
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.component.styling.ChipStyle
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import org.jetbrains.jewel.ui.component.styling.DividerStyle
import org.jetbrains.jewel.ui.component.styling.DropdownStyle
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle
import org.jetbrains.jewel.ui.component.styling.HorizontalProgressBarStyle
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalCheckboxStyle
import org.jetbrains.jewel.ui.component.styling.LocalChipStyle
import org.jetbrains.jewel.ui.component.styling.LocalCircularProgressStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultDropdownStyle
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalDividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalEditorTabStyle
import org.jetbrains.jewel.ui.component.styling.LocalGroupHeaderStyle
import org.jetbrains.jewel.ui.component.styling.LocalHorizontalProgressBarStyle
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalLazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.LocalLinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalMenuStyle
import org.jetbrains.jewel.ui.component.styling.LocalOutlinedButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalRadioButtonStyle
import org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextAreaStyle
import org.jetbrains.jewel.ui.component.styling.LocalTextFieldStyle
import org.jetbrains.jewel.ui.component.styling.LocalTooltipStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.component.styling.RadioButtonStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

val JewelTheme.Companion.colorPalette: ThemeColorPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalColorPalette.current

val JewelTheme.Companion.iconData: ThemeIconData
    @Composable
    @ReadOnlyComposable
    get() = LocalIconData.current

// -----------------
// Component styling
// -----------------

val JewelTheme.Companion.defaultButtonStyle: ButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalDefaultButtonStyle.current

val JewelTheme.Companion.outlinedButtonStyle: ButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalOutlinedButtonStyle.current

val JewelTheme.Companion.checkboxStyle: CheckboxStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalCheckboxStyle.current

val JewelTheme.Companion.chipStyle: ChipStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalChipStyle.current

val JewelTheme.Companion.dividerStyle: DividerStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalDividerStyle.current

val JewelTheme.Companion.dropdownStyle: DropdownStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalDefaultDropdownStyle.current

val JewelTheme.Companion.groupHeaderStyle: GroupHeaderStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalGroupHeaderStyle.current

val JewelTheme.Companion.linkStyle: LinkStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalLinkStyle.current

val JewelTheme.Companion.menuStyle: MenuStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalMenuStyle.current

val JewelTheme.Companion.horizontalProgressBarStyle: HorizontalProgressBarStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalHorizontalProgressBarStyle.current

val JewelTheme.Companion.radioButtonStyle: RadioButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioButtonStyle.current

val JewelTheme.Companion.scrollbarStyle: ScrollbarStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalScrollbarStyle.current

val JewelTheme.Companion.textAreaStyle: TextAreaStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTextAreaStyle.current

val JewelTheme.Companion.textFieldStyle: TextFieldStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTextFieldStyle.current

val JewelTheme.Companion.treeStyle: LazyTreeStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalLazyTreeStyle.current

val JewelTheme.Companion.defaultTabStyle: TabStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalDefaultTabStyle.current

val JewelTheme.Companion.editorTabStyle: TabStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalEditorTabStyle.current

val JewelTheme.Companion.circularProgressStyle: CircularProgressStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalCircularProgressStyle.current

val JewelTheme.Companion.tooltipStyle: TooltipStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTooltipStyle.current

val JewelTheme.Companion.iconButtonStyle: IconButtonStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalIconButtonStyle.current

@Composable
fun BaseJewelTheme(
    theme: ThemeDefinition,
    styling: ComponentStyling,
    content: @Composable () -> Unit,
) {
    BaseJewelTheme(theme, styling, swingCompatMode = false, content)
}

@Composable
fun BaseJewelTheme(
    theme: ThemeDefinition,
    styling: ComponentStyling,
    swingCompatMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    JewelTheme(theme, swingCompatMode) {
        CompositionLocalProvider(
            LocalColorPalette provides theme.colorPalette,
            LocalIconData provides theme.iconData,
            LocalIndication provides NoIndication,
        ) {
            CompositionLocalProvider(values = styling.styles(), content = content)
        }
    }
}
