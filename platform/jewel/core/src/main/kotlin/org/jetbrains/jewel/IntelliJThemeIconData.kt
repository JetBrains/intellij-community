package org.jetbrains.jewel

interface IntelliJThemeIconData {

    val iconOverrides: Map<String, String>
    val colorPalette: Map<String, String>
}

object EmptyThemeIconData : IntelliJThemeIconData {

    override val iconOverrides: Map<String, String> = emptyMap()

    override val colorPalette: Map<String, String> = emptyMap()

    override fun toString() = "EmptyThemeIconData(iconOverrides=[], colorPalette=[])"
}
