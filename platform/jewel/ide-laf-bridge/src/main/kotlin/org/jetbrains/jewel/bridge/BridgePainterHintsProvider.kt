package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.intellij.ide.ui.UITheme
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.NewUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PalettePainterHintsProvider
import org.jetbrains.jewel.ui.painter.hints.ColorBasedPaletteReplacement
import org.jetbrains.jewel.ui.painter.hints.Dark
import org.jetbrains.jewel.ui.painter.hints.HiDpi
import org.jetbrains.jewel.ui.painter.hints.KeyBasedPaletteReplacement
import org.jetbrains.jewel.ui.util.toRgbaHexString

/**
 * Provide the default [PainterHint]s to use in the IDE.
 *
 * This is an internal Jewel API and should not be used directly.
 */
@ApiStatus.Internal
@InternalJewelApi
public class BridgePainterHintsProvider
private constructor(
    isDark: Boolean,
    intellijIconPalette: Map<String, String?> = emptyMap(),
    themeIconPalette: Map<String, String?> = emptyMap(),
    themeColorPalette: Map<String, Color?> = emptyMap(),
) : PalettePainterHintsProvider(isDark, intellijIconPalette, themeIconPalette, themeColorPalette) {
    override val checkBoxByColorPaletteHint: PainterHint
    override val checkBoxByKeyPaletteHint: PainterHint
    override val treePaletteHint: PainterHint
    override val uiPaletteHint: PainterHint

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
            // format, starting from IJP 241. This is only enabled for the New UI.
            if (key.startsWith("Checkbox.") && NewUI.isEnabled()) {
                registerIdBasedReplacement(checkBoxesByKey, key, value)
            }

            val map = selectMap(key, checkBoxesByColor, trees, ui) ?: continue
            registerColorBasedReplacement(map, key, value)
        }

        checkBoxByKeyPaletteHint = KeyBasedPaletteReplacement(checkBoxesByKey)
        checkBoxByColorPaletteHint = ColorBasedPaletteReplacement(checkBoxesByColor)
        treePaletteHint = ColorBasedPaletteReplacement(trees)
        uiPaletteHint = ColorBasedPaletteReplacement(ui)
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
            logger.warn(
                "${if (isDark) "Dark" else "Light"} theme: color key $key is deprecated, use $adjustedKey instead"
            )
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
        add(BridgeOverride)
        add(getPaletteHint(path, isNewUi = isNewUiTheme()))
        add(HiDpi())
        add(Dark(JewelTheme.isDark))
    }

    public companion object {
        private val logger = thisLogger()

        @Suppress("UnstableApiUsage") // We need to call @Internal APIs
        public operator fun invoke(isDark: Boolean): PalettePainterHintsProvider {
            val uiTheme = currentUiThemeOrNull() ?: return BridgePainterHintsProvider(isDark)
            logger.info("Parsing theme info from theme ${uiTheme.name} (id: ${uiTheme.id}, isDark: ${uiTheme.isDark})")

            val bean = uiTheme.describe()
            val iconColorPalette =
                (bean.colorPalette as Map<String, Any?>).mapValues {
                    when (val value = it.value) {
                        is String -> value
                        is java.awt.Color -> value.toRgbaHexString(omitAlphaWhenFullyOpaque = true)
                        else -> null
                    }
                }
            val keyPalette = UITheme.getColorPalette()
            val themeColors = bean.colors.mapValues { (_, v) -> Color(v) }

            return BridgePainterHintsProvider(
                isDark = isDark,
                intellijIconPalette = keyPalette,
                themeIconPalette = iconColorPalette,
                themeColorPalette = themeColors,
            )
        }

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
