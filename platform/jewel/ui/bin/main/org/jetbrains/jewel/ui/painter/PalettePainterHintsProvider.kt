package org.jetbrains.jewel.ui.painter

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.util.fromRgbaHexStringOrNull

/** Provides the default [PainterHint]s to use to load images. */
public abstract class PalettePainterHintsProvider(
    protected val isDark: Boolean,
    protected val intellijIconPalette: Map<String, String?>,
    protected val themeIconPalette: Map<String, String?>,
    protected val themeColorPalette: Map<String, Color?>,
) : PainterHintsProvider {
    protected abstract val checkBoxByKeyPaletteHint: PainterHint
    protected abstract val checkBoxByColorPaletteHint: PainterHint
    protected abstract val treePaletteHint: PainterHint
    protected abstract val uiPaletteHint: PainterHint

    protected fun resolveColor(value: String): Color? {
        // If the value is one of the named colors in the theme, use that named color's value
        val namedColor = themeColorPalette[value]
        return namedColor ?: Color.fromRgbaHexStringOrNull(value)
    }

    // See com.intellij.ide.ui.UITheme.toColorString
    protected fun resolveKeyColor(key: String, keyPalette: Map<String, String?>, isDark: Boolean): Color? {
        val darkKey = "$key.Dark"
        val resolvedKey = if (isDark && keyPalette.containsKey(darkKey)) darkKey else key
        return Color.fromRgbaHexStringOrNull(keyPalette[resolvedKey] ?: return null)
    }

    protected fun selectMap(
        key: String,
        checkboxes: MutableMap<Color, Color>,
        trees: MutableMap<Color, Color>,
        ui: MutableMap<Color, Color>,
    ): MutableMap<Color, Color>? =
        when {
            key.startsWith("Checkbox.") -> checkboxes
            key.startsWith("Tree.iconColor.") -> trees
            key.startsWith("Objects.") || key.startsWith("Actions.") || key.startsWith("#") -> ui
            else -> null
        }

    /**
     * Returns a [PainterHint] that can be used to patch colors for a resource with a given [path].
     *
     * The implementations vary depending on the path, and when running on the IntelliJ Platform, also on the IDE
     * version and the current theme (New UI vs Classic UI).
     */
    protected fun getPaletteHint(path: String, isNewUi: Boolean): PainterHint {
        if (!path.contains("com/intellij/ide/ui/laf/icons/") && !path.contains("themes/expUI/icons/dark/"))
            return uiPaletteHint

        val file = path.substringAfterLast('/')
        // ⚠️ This next line is not a copy-paste error — the code in
        // UITheme.PaletteScopeManager.getScopeByPath()
        // says they share the same colors
        val isCheckboxScope = file.startsWith("check") || file.startsWith("radio")

        return when {
            file == "treeCollapsed.svg" || file == "treeExpanded.svg" -> treePaletteHint
            isNewUi && isCheckboxScope -> checkBoxByKeyPaletteHint
            !isNewUi && isCheckboxScope -> checkBoxByColorPaletteHint
            else -> PainterHint.None
        }
    }
}
