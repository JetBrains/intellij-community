package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Immutable
import com.intellij.ide.ui.UITheme
import org.jetbrains.jewel.IntelliJThemeIconData

@Immutable
internal class BridgeIconData(
    override val iconOverrides: Map<String, String>,
    override val colorPalette: Map<String, String>,
    override val selectionColorPalette: Map<String, String>,
) : IntelliJThemeIconData {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BridgeIconData

        if (iconOverrides != other.iconOverrides) return false
        if (colorPalette != other.colorPalette) return false
        if (selectionColorPalette != other.selectionColorPalette) return false

        return true
    }

    override fun hashCode(): Int {
        var result = iconOverrides.hashCode()
        result = 31 * result + colorPalette.hashCode()
        result = 31 * result + selectionColorPalette.hashCode()
        return result
    }

    override fun toString(): String =
        "BridgeIconData(iconOverrides=$iconOverrides, colorPalette=$colorPalette, " +
            "selectionColorPalette=$selectionColorPalette)"

    companion object {

        fun readFromLaF(): BridgeIconData {
            val uiTheme = currentUiThemeOrNull()
            val iconMap = uiTheme?.icons.orEmpty()
            val selectedIconColorPalette = uiTheme?.selectedIconColorPalette.orEmpty()

            val colorPalette = UITheme.getColorPalette()
            return BridgeIconData(iconMap, colorPalette, selectedIconColorPalette)
        }
    }
}
