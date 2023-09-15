package org.jetbrains.jewel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Replicates com.intellij.ide.ui.UITheme.PaletteScopeManager's functionality
// (note that in Swing, there is also a RadioButtons scope, but while it gets
// written to, it never gets accessed by the actual color patching, so we ignore
// writing any Radio button-related entries and read the CheckBox values for them)
@Immutable
class PaletteMapper(
    private val ui: Scope,
    private val checkBoxes: Scope,
    private val trees: Scope,
) {

    fun getScopeForPath(path: String?): Scope? {
        if (path == null) return ui
        if (!path.contains("com/intellij/ide/ui/laf/icons/")) return ui

        val file = path.substringAfterLast('/')
        return when {
            file == "treeCollapsed.svg" || file == "treeExpanded.svg" -> trees
            // ⚠️ This next line is not a copy-paste error — the code in UITheme.PaletteScopeManager.getScopeByPath()
            // says they share the same colors
            file.startsWith("check") || file.startsWith("radio") -> checkBoxes
            else -> null
        }
    }

    companion object {

        val Empty = PaletteMapper(Scope.Empty, Scope.Empty, Scope.Empty)
    }

    @Immutable
    @JvmInline
    value class Scope(val colorOverrides: Map<Color, Color>) {

        fun mapColorOrNull(originalColor: Color): Color? =
            colorOverrides[originalColor]

        override fun toString(): String = "PaletteMapper.Scope(colorOverrides=$colorOverrides)"

        companion object {

            val Empty = Scope(emptyMap())
        }
    }
}
