package org.jetbrains.jewel.intui.standalone

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.ui.painter.BasePainterHintsProvider
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.hints.Dark
import org.jetbrains.jewel.ui.painter.hints.HiDpi
import org.jetbrains.jewel.ui.painter.hints.Override

public class StandalonePainterHintsProvider(
    theme: ThemeDefinition,
) : BasePainterHintsProvider(
    theme.isDark,
    intellijColorPalette,
    theme.iconData.colorPalette,
    theme.colorPalette.rawMap,
) {

    private val overrideHint: PainterHint =
        Override(
            theme.iconData.iconOverrides.entries.associate { (k, v) ->
                k.removePrefix("/") to v.removePrefix("/")
            },
        )

    @Composable
    override fun hints(path: String): List<PainterHint> = buildList {
        add(getPaletteHint(path))
        add(overrideHint)
        add(HiDpi())
        add(Dark(JewelTheme.isDark))
    }

    public companion object {

        // Extracted from com.intellij.ide.ui.UITheme#colorPalette
        private val intellijColorPalette =
            mapOf(
                "Actions.Red" to "#DB5860",
                "Actions.Red.Dark" to "#C75450",
                "Actions.Yellow" to "#EDA200",
                "Actions.Yellow.Dark" to "#F0A732",
                "Actions.Green" to "#59A869",
                "Actions.Green.Dark" to "#499C54",
                "Actions.Blue" to "#389FD6",
                "Actions.Blue.Dark" to "#3592C4",
                "Actions.Grey" to "#6E6E6E",
                "Actions.Grey.Dark" to "#AFB1B3",
                "Actions.GreyInline" to "#7F8B91",
                "Actions.GreyInline.Dark" to "#7F8B91",
                "Objects.Grey" to "#9AA7B0",
                "Objects.Blue" to "#40B6E0",
                "Objects.Green" to "#62B543",
                "Objects.Yellow" to "#F4AF3D",
                "Objects.YellowDark" to "#D9A343",
                "Objects.Purple" to "#B99BF8",
                "Objects.Pink" to "#F98B9E",
                "Objects.Red" to "#F26522",
                "Objects.RedStatus" to "#E05555",
                "Objects.GreenAndroid" to "#3DDC84",
                "Objects.BlackText" to "#231F20",
                "Checkbox.Background.Default" to "#FFFFFF",
                "Checkbox.Background.Default.Dark" to "#43494A",
                "Checkbox.Background.Disabled" to "#F2F2F2",
                "Checkbox.Background.Disabled.Dark" to "#3C3F41",
                "Checkbox.Border.Default" to "#b0b0b0",
                "Checkbox.Border.Default.Dark" to "#6B6B6B",
                "Checkbox.Border.Disabled" to "#BDBDBD",
                "Checkbox.Border.Disabled.Dark" to "#545556",
                "Checkbox.Focus.Thin.Default" to "#7B9FC7",
                "Checkbox.Focus.Thin.Default.Dark" to "#466D94",
                "Checkbox.Focus.Wide" to "#97C3F3",
                "Checkbox.Focus.Wide.Dark" to "#3D6185",
                "Checkbox.Foreground.Disabled" to "#ABABAB",
                "Checkbox.Foreground.Disabled.Dark" to "#606060",
                "Checkbox.Background.Selected" to "#4F9EE3",
                "Checkbox.Background.Selected.Dark" to "#43494A",
                "Checkbox.Border.Selected" to "#4B97D9",
                "Checkbox.Border.Selected.Dark" to "#6B6B6B",
                "Checkbox.Foreground.Selected" to "#FEFEFE",
                "Checkbox.Foreground.Selected.Dark" to "#A7A7A7",
                "Checkbox.Focus.Thin.Selected" to "#ACCFF7",
                "Checkbox.Focus.Thin.Selected.Dark" to "#466D94",
                "Tree.iconColor" to "#808080",
                "Tree.iconColor.Dark" to "#AFB1B3",
            )
    }
}
