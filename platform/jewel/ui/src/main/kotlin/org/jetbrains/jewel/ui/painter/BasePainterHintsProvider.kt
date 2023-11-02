package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.painter.hints.Palette
import org.jetbrains.jewel.ui.util.fromRGBAHexStringOrNull

public abstract class BasePainterHintsProvider(
    isDark: Boolean,
    intellijIconPalette: Map<String, String?>,
    themeIconPalette: Map<String, String?>,
    themeColorPalette: Map<String, Color?>,
) : PainterHintsProvider {

    private val checkBoxPaletteHint: PainterHint
    private val treePaletteHint: PainterHint
    private val uiPaletteHint: PainterHint

    init {
        val ui = mutableMapOf<Color, Color>()
        val checkBoxes = mutableMapOf<Color, Color>()
        val trees = mutableMapOf<Color, Color>()

        @Suppress("LoopWithTooManyJumpStatements")
        for ((key, value) in themeIconPalette) {
            value ?: continue
            val map = selectMap(key, checkBoxes, trees, ui) ?: continue

            // If the value is one of the named colors in the theme, use that named color's value
            val namedColor = themeColorPalette[value]

            // If either the key or the resolved value aren't valid colors, ignore the entry
            val keyAsColor = resolveKeyColor(key, intellijIconPalette, isDark) ?: continue
            val resolvedColor = namedColor ?: Color.fromRGBAHexStringOrNull(value) ?: continue

            // Save the new entry (oldColor -> newColor) in the map
            map[keyAsColor] = resolvedColor
        }

        checkBoxPaletteHint = Palette(checkBoxes)
        treePaletteHint = Palette(trees)
        uiPaletteHint = Palette(ui)
    }

    private fun selectMap(
        key: String,
        checkBoxes: MutableMap<Color, Color>,
        trees: MutableMap<Color, Color>,
        ui: MutableMap<Color, Color>,
    ) =
        when {
            key.startsWith("Checkbox.") -> checkBoxes
            key.startsWith("Tree.iconColor.") -> trees
            key.startsWith("Objects.") || key.startsWith("Actions.") || key.startsWith("#") -> ui
            else -> null
        }

    // See com.intellij.ide.ui.UITheme.toColorString
    private fun resolveKeyColor(
        key: String,
        keyPalette: Map<String, String?>,
        isDark: Boolean,
    ): Color? {
        val darkKey = "$key.Dark"
        val resolvedKey = if (isDark && keyPalette.containsKey(darkKey)) darkKey else key
        return Color.fromRGBAHexStringOrNull(keyPalette[resolvedKey] ?: return null)
    }

    protected fun getPaletteHint(path: String): PainterHint {
        if (!path.contains("com/intellij/ide/ui/laf/icons/")) return uiPaletteHint

        val file = path.substringAfterLast('/')
        return when {
            file == "treeCollapsed.svg" || file == "treeExpanded.svg" -> treePaletteHint
            // ⚠️ This next line is not a copy-paste error — the code in
            // UITheme.PaletteScopeManager.getScopeByPath()
            // says they share the same colors
            file.startsWith("check") || file.startsWith("radio") -> checkBoxPaletteHint
            else -> PainterHint.None
        }
    }
}
