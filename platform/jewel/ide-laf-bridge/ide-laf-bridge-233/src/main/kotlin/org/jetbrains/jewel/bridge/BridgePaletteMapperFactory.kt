package org.jetbrains.jewel.bridge

import com.intellij.ide.ui.UITheme
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.jewel.PaletteMapper
import org.jetbrains.jewel.themes.PaletteMapperFactory

object BridgePaletteMapperFactory : PaletteMapperFactory() {

    private val logger = thisLogger()

    @Suppress("UnstableApiUsage")
    fun create(isDark: Boolean): PaletteMapper {
        // If we can't read the current theme, no mapping is possible
        val uiTheme = currentUiThemeOrNull() ?: return PaletteMapper.Empty
        logger.info("Parsing theme info from theme ${uiTheme.name} (id: ${uiTheme.id}, isDark: ${uiTheme.isDark})")

        val bean = uiTheme.describe()

        // TODO: in New UI + Dark theme values can be non String, but IJColorUIResource
        //  since these are deserialized values simply casted to Map<String, String>.
        //  Let's handle it while it is not fixed in platform
        val iconColorPalette = (bean.colorPalette as Map<String, Any?>).mapValues {
            when (val value = it.value) {
                is String -> value
                else -> null
            }
        }
        val keyPalette = UITheme.getColorPalette()
        val themeColors = bean.colors

        return createInternal(iconColorPalette, keyPalette, themeColors, isDark)
    }

    override fun logInfo(message: String) {
        logger.info(message)
    }
}
