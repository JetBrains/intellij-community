package org.jetbrains.jewel.bridge

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.laf.UIThemeBasedLookAndFeelInfo
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.jewel.InternalJewelApi
import java.lang.reflect.Field

private val logger = Logger.getInstance("UiThemeExtensions")

private val classUITheme
    get() = UITheme::class.java

@Suppress("UnstableApiUsage")
@InternalJewelApi
internal fun currentUiThemeOrNull() =
    (LafManager.getInstance().currentLookAndFeel as? UIThemeBasedLookAndFeelInfo)?.theme

// TODO #116 replace with public API access once it's made available (IJP 233?)
@InternalJewelApi
val UITheme.icons: Map<String, String>
    get() = readMapField<String>(classUITheme.getDeclaredField("icons"))
        .filterKeys { it != "ColorPalette" }

// TODO #116 replace with public API access once it's made available (IJP 233?)
internal val UITheme.iconColorPalette: Map<String, String>
    get() = readMapField<Map<String, String>>(classUITheme.getDeclaredField("icons"))
        .get("ColorPalette").orEmpty()

// TODO #116 replace with public API access once it's made available (IJP 233?)
internal val UITheme.selectedIconColorPalette: Map<String, String>
    get() = readMapField(classUITheme.getDeclaredField("iconColorsOnSelection"))

private fun <T> UITheme.readMapField(field: Field): Map<String, T> {
    @Suppress("DEPRECATION") // We don't have an alternative API to use
    val wasAccessible = field.isAccessible
    field.isAccessible = true

    return try {
        @Suppress("UNCHECKED_CAST")
        (field.get(this) as? Map<String, T>).orEmpty()
    } catch (e: IllegalAccessException) {
        logger.warn("Error while retrieving LaF", e)
        emptyMap()
    } finally {
        field.isAccessible = wasAccessible
    }
}
