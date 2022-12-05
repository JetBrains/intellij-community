package org.jetbrains.jewel.util.font

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FileFont
import androidx.compose.ui.text.platform.Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment
import java.awt.font.TextAttribute
import java.io.File
import java.util.Locale
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import java.awt.Font as AwtFont
import java.awt.Font.createFont as createAwtFont

object FontsLoader {

    suspend fun loadFontsFrom(fontFileProviders: List<FileProvider>): Map<String, SystemFontFamily> {

        return collectIntoSystemFontFamilies(
            fontFamilyNames = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames(Locale.ROOT)
                .toList(),
            fontFiles = fontFileProviders.parallelMap(Dispatchers.IO) { it.provider() }
                .parallelMap(Dispatchers.IO) { runCatching { createAwtFont(AwtFont.TRUETYPE_FONT, it) }.getOrNull() to it }
                .mapNotNull { (key, value) -> key?.let { it to value } }
                .toMap()
        )
    }

    private suspend fun <T, R> Iterable<T>.parallelMap(
        context: CoroutineContext = EmptyCoroutineContext,
        transform: suspend (T) -> R
    ) =
        if (context != EmptyCoroutineContext) {
            withContext(context) { map { async { transform(it) } }.awaitAll() }
        } else {
            coroutineScope { map { async { transform(it) } }.awaitAll() }
        }

    private fun collectIntoSystemFontFamilies(
        fontFamilyNames: Iterable<String>,
        fontFiles: Map<AwtFont, File>
    ): Map<String, SystemFontFamily> {
        val sortedFontFamilyNames = fontFamilyNames.sortedByDescending { it.length }
        val fontFamilies = mutableMapOf<String, SystemFontFamily>()
        val filesByFont = fontFiles.toMutableMap()

        for (familyName in sortedFontFamilyNames) {
            val files = filesByFont.filterKeys { font -> familyName.equals(font.getFamily(Locale.ENGLISH), ignoreCase = true) }

            for ((font, _) in files) {
                filesByFont.remove(font)
            }

            if (files.isEmpty()) {
                continue
            }

            val fileFonts = files.map { (font, file) ->
                val fontName = font.getFontName(Locale.ENGLISH)
                val fontStyle = if (font.isItalic || looksItalic(fontName)) FontStyle.Italic else FontStyle.Normal
                val rawWeight = fontWeightFromTextAttributeValue(font.attributes[TextAttribute.WEIGHT] as Float?)
                val fontWeight = rawWeight ?: inferWeightFromName(
                    fontName.substringAfter(font.getFamily(Locale.ENGLISH)).split(' ', '-')
                        .map { it.trim().lowercase() }
                        .filter { it.isNotBlank() }
                )

                Font(file = file, weight = fontWeight, style = fontStyle) as FileFont
            }

            fontFamilies[familyName] = SystemFontFamily(familyName, FontFamily(fileFonts), fileFonts)
        }

        return fontFamilies
    }

    private fun looksItalic(name: String): Boolean = name.trimEnd().endsWith("italic", ignoreCase = true)

    // The mappings are somewhat arbitrary, and may look wrong, but this just going in order on both sides
    fun fontWeightFromTextAttributeValue(weightValue: Float?): FontWeight? =
        when (weightValue) {
            TextAttribute.WEIGHT_EXTRA_LIGHT -> FontWeight.Thin
            TextAttribute.WEIGHT_LIGHT -> FontWeight.ExtraLight
            TextAttribute.WEIGHT_DEMILIGHT -> FontWeight.Light
            TextAttribute.WEIGHT_REGULAR -> FontWeight.Normal
            TextAttribute.WEIGHT_SEMIBOLD -> FontWeight.Medium
            TextAttribute.WEIGHT_MEDIUM -> FontWeight.SemiBold
            TextAttribute.WEIGHT_BOLD -> FontWeight.Bold
            TextAttribute.WEIGHT_HEAVY, TextAttribute.WEIGHT_EXTRABOLD -> FontWeight.ExtraBold
            TextAttribute.WEIGHT_ULTRABOLD -> FontWeight.Black
            else -> null
        }

    private fun inferWeightFromName(nameTokens: List<String>): FontWeight =
        when {
            nameTokens.any { it.startsWith("thin") || it == "100" } -> FontWeight.Thin
            nameTokens.any {
                it.startsWith("extralight") || it.startsWith("semilight") || it.startsWith("extra light")
                    || it.startsWith("semi light") || it.startsWith("extra-light") || it.startsWith("semi-light") || it == "200"
            } -> FontWeight.ExtraLight
            nameTokens.any { it.startsWith("light") || it == "300" } -> FontWeight.Light
            nameTokens.any { it.startsWith("medium") || it == "500" } -> FontWeight.Medium
            nameTokens.any { it.startsWith("semibold") || it.startsWith("semi bold") || it.startsWith("semi-bold") || it == "600" } -> FontWeight.SemiBold
            nameTokens.any { it.startsWith("bold") || it == "700" } -> FontWeight.Bold
            nameTokens.any {
                it.startsWith("extrabold") || it.startsWith("extra bold") || it.startsWith("extra-bold")
                    || it.startsWith("heavy") || it == "800"
            } -> FontWeight.ExtraBold
            nameTokens.any { it.startsWith("black") || it == "900" } -> FontWeight.Black
            else -> FontWeight.Normal
        }
}
