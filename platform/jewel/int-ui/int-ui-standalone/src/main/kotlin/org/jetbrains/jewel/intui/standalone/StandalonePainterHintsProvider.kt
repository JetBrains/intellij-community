package org.jetbrains.jewel.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.util.myLogger
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PalettePainterHintsProvider
import org.jetbrains.jewel.ui.painter.hints.ColorBasedPaletteReplacement
import org.jetbrains.jewel.ui.painter.hints.ColorKeyPaletteReplacement
import org.jetbrains.jewel.ui.painter.hints.Dark
import org.jetbrains.jewel.ui.painter.hints.HiDpi
import org.jetbrains.jewel.ui.painter.hints.KeyBasedPaletteReplacement
import org.jetbrains.jewel.ui.painter.hints.PathOverride

/** Provides the default [PainterHint]s to use to load images. */
public class StandalonePainterHintsProvider(theme: ThemeDefinition) :
    PalettePainterHintsProvider(
        theme.isDark,
        intellijColorPalette,
        theme.iconData.colorPalette,
        theme.colorPalette.rawMap,
    ) {
    private val logger = myLogger()

    override val checkBoxByColorPaletteHint: PainterHint
    override val checkBoxByKeyPaletteHint: PainterHint
    override val treePaletteHint: PainterHint
    override val uiPaletteHint: PainterHint
    override val togglePaletteHint: PainterHint

    private val overrideHint: PainterHint =
        PathOverride(
            theme.iconData.iconOverrides.entries.associate { (k, v) -> k.removePrefix("/") to v.removePrefix("/") }
        )

    init {
        val ui = mutableMapOf<Color, Color>()
        val checkBoxesByColor = mutableMapOf<Color, Color>()
        val checkBoxesByKey = mutableMapOf<String, Color>()
        val trees = mutableMapOf<Color, Color>()

        @Suppress("LoopWithTooManyJumpStatements")
        for ((key, value) in themeIconPalette) {
            if (value == null) continue

            // Checkbox (and radio button) entries work differently: the ID field
            // for each element that needs patching has a "[fillKey]_[strokeKey]"
            // format, starting from IJP 241.
            if (key.startsWith("Checkbox.")) {
                registerIdBasedReplacement(checkBoxesByKey, key, value)
            }

            val map = selectMap(key, checkBoxesByColor, trees, ui) ?: continue
            registerColorBasedReplacement(map, key, value)
        }

        checkBoxByKeyPaletteHint = KeyBasedPaletteReplacement(checkBoxesByKey)
        checkBoxByColorPaletteHint = ColorBasedPaletteReplacement(checkBoxesByColor)
        treePaletteHint = ColorBasedPaletteReplacement(trees)
        uiPaletteHint = ColorBasedPaletteReplacement(ui)

        val toggleDefaults = if (isDark) darkToggleColors else lightToggleColors
        val themeToggleColors =
            themeColorPalette.mapNotNull { (key, color) ->
                if (key.startsWith("toggle-")) color?.let { key to it } else null
            }
        togglePaletteHint = ColorKeyPaletteReplacement(toggleDefaults + themeToggleColors)
    }

    private fun registerColorBasedReplacement(map: MutableMap<Color, Color>, key: String, value: String) {
        // If either the key or the resolved value aren't valid colors, ignore the entry
        val keyAsColor = resolveKeyColor(key, intellijIconPalette, isDark) ?: return
        val resolvedColor = resolveColor(value) ?: return

        // Save the new entry (oldColor -> newColor) in the map
        map[keyAsColor] = resolvedColor
    }

    private fun registerIdBasedReplacement(map: MutableMap<String, Color>, key: String, value: String) {
        val adjustedKey = if (isDark) key.removeSuffix(".Dark") else key

        if (adjustedKey !in supportedCheckboxKeys) {
            logger.debug("${if (isDark) "Dark" else "Light"} theme: color key $key is not supported, will be ignored")
            return
        }

        if (adjustedKey != key) {
            // adjustedKey differs from key only when the ".Dark" suffix was stripped, which happens
            // only when isDark is true, so this branch is dark-theme-only by construction.
            logger.warn("Dark theme: color key $key is deprecated, use $adjustedKey instead")
        }

        val parsedValue = resolveColor(value)
        if (parsedValue == null) {
            logger.info("${if (isDark) "Dark" else "Light"} theme: color key $key has invalid value: '$value'")
            return
        }

        map[adjustedKey] = parsedValue
    }

    @Composable
    override fun hints(path: String): List<PainterHint> = buildList {
        add(overrideHint)
        add(getPaletteHint(path, isNewUi = true))
        add(HiDpi())
        add(Dark(JewelTheme.isDark))
    }

    /** Companion object for [StandalonePainterHintsProvider]. */
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

        // The toggle-* colors of the IntelliJ theme (platform-resources/src/themes/intellijlaf.theme.json).
        // expUI_light.theme.json does not define any toggle-* colors, it inherits from intellijlaf.theme.json.
        private val lightToggleColors =
            mapOf(
                "toggle-on-bg" to Color(0xFF3871E1),
                "toggle-off-bg" to Color(0x30000000),
                "toggle-off-border" to Color(0x00FFFFFF),
                "toggle-on-border" to Color(0xFF3871E1),
                "toggle-on-disabled-bg" to Color(0xFFF7F8F9),
                "toggle-off-disabled-bg" to Color(0xFFF7F8F9),
                "toggle-on-disabled-border" to Color(0xFFDDDFE4),
                "toggle-off-disabled-border" to Color(0xFFDDDFE4),
                "toggle-focus-border" to Color(0xFF3871E1),
                "toggle-on-notch" to Color(0xFFFFFFFF),
                "toggle-off-notch" to Color(0xFFFFFFFF),
                "toggle-on-disabled-notch" to Color(0xFFC3C5CB),
                "toggle-off-disabled-notch" to Color(0xFFC3C5CB),
            )

        // The toggle-* colors of the Int UI dark theme (platform-resources/src/themes/expUI/expUI_dark.theme.json)
        private val darkToggleColors =
            mapOf(
                "toggle-on-bg" to Color(0xFF3871E1),
                "toggle-off-bg" to Color(0x3BFFFFFF),
                "toggle-off-border" to Color(0x00191A1C),
                "toggle-on-border" to Color(0xFF3871E1),
                "toggle-on-disabled-bg" to Color(0x00191A1C),
                "toggle-off-disabled-bg" to Color(0x00191A1C),
                "toggle-on-disabled-border" to Color(0xFF33353B),
                "toggle-off-disabled-border" to Color(0xFF33353B),
                "toggle-focus-border" to Color(0xFF3871E1),
                "toggle-on-notch" to Color(0xFFFFFFFF),
                "toggle-off-notch" to Color(0xFFD1D3D9),
                "toggle-on-disabled-notch" to Color(0xFF5F6269),
                "toggle-off-disabled-notch" to Color(0xFF5F6269),
            )

        private val supportedCheckboxKeys: Set<String> =
            setOf(
                "Checkbox.Background.Default",
                "Checkbox.Border.Default",
                "Checkbox.Foreground.Selected",
                "Checkbox.Background.Selected",
                "Checkbox.Border.Selected",
                "Checkbox.Focus.Wide",
                "Checkbox.Foreground.Disabled",
                "Checkbox.Background.Disabled",
                "Checkbox.Border.Disabled",
            )
    }
}
