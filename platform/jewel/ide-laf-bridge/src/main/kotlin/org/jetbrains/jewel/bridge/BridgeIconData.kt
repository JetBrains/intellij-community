package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Immutable
import org.jetbrains.jewel.IntelliJThemeIconData

@Immutable
internal class BridgeIconData(
    override val iconOverrides: Map<String, String>,
    override val colorPalette: Map<String, String>,
) : IntelliJThemeIconData {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BridgeIconData

        if (iconOverrides != other.iconOverrides) return false
        if (colorPalette != other.colorPalette) return false

        return true
    }

    override fun hashCode(): Int {
        var result = iconOverrides.hashCode()
        result = 31 * result + colorPalette.hashCode()
        return result
    }

    override fun toString(): String =
        "BridgeIconData(iconOverrides=$iconOverrides, colorPalette=$colorPalette)"

    companion object {

        // TODO retrieve icon data from Swing
        fun readFromLaF() = BridgeIconData(emptyMap(), emptyMap())
    }
}
