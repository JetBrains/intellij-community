package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.unit.sp
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import org.jetbrains.jewel.bridge.retrieveEditorColorScheme
import org.jetbrains.jewel.bridge.retrieveTextStyle
import org.jetbrains.jewel.bridge.toComposeColor

public fun retrieveDefaultTextStyle(): TextStyle = retrieveTextStyle("Label.font", "Label.foreground")

@OptIn(ExperimentalTextApi::class)
public fun retrieveEditorTextStyle(): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()

    val fontSize = editorColorScheme.editorFontSize.sp
    return retrieveDefaultTextStyle()
        .copy(
            color = editorColorScheme.defaultForeground.toComposeColor(),
            fontFamily = editorColorScheme.getFont(EditorFontType.PLAIN).asComposeFontFamily(),
            fontSize = fontSize,
            lineHeight = fontSize * editorColorScheme.lineSpacing,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0" else "liga 1",
        )
}

@OptIn(ExperimentalTextApi::class)
public fun retrieveConsoleTextStyle(): TextStyle {
    val editorColorScheme = retrieveEditorColorScheme()
    if (editorColorScheme.isUseEditorFontPreferencesInConsole) return retrieveEditorTextStyle()

    val fontSize = editorColorScheme.consoleFontSize.sp
    val fontColor =
        editorColorScheme.getColor(ColorKey.createColorKey("BLOCK_TERMINAL_DEFAULT_FOREGROUND"))
            ?: editorColorScheme.defaultForeground

    return retrieveDefaultTextStyle()
        .copy(
            color = fontColor.toComposeColor(),
            fontFamily = editorColorScheme.getFont(EditorFontType.CONSOLE_PLAIN).asComposeFontFamily(),
            fontSize = fontSize,
            lineHeight = fontSize * editorColorScheme.lineSpacing,
            fontFeatureSettings = if (!editorColorScheme.isUseLigatures) "liga 0" else "liga 1",
        )
}
