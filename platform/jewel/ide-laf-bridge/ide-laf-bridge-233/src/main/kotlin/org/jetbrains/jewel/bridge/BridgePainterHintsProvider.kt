package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.intellij.ide.ui.UITheme
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.painter.BasePainterHintsProvider
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.hints.Dark
import org.jetbrains.jewel.ui.painter.hints.HiDpi

@InternalJewelApi
class BridgePainterHintsProvider private constructor(
    isDark: Boolean,
    intellijIconPalette: Map<String, String?> = emptyMap(),
    themeIconPalette: Map<String, String?> = emptyMap(),
    themeColorPalette: Map<String, Color?> = emptyMap(),
) : BasePainterHintsProvider(isDark, intellijIconPalette, themeIconPalette, themeColorPalette) {

    @Composable
    override fun hints(path: String): List<PainterHint> = buildList {
        add(getPaletteHint(path))
        add(BridgeOverride)
        add(HiDpi())
        add(Dark(JewelTheme.isDark))
    }

    companion object {

        private val logger = thisLogger()

        operator fun invoke(isDark: Boolean): BasePainterHintsProvider {
            val uiTheme = currentUiThemeOrNull() ?: return BridgePainterHintsProvider(isDark)
            logger.info("Parsing theme info from theme ${uiTheme.name} (id: ${uiTheme.id}, isDark: ${uiTheme.isDark})")

            val bean = uiTheme.describe()
            val iconColorPalette =
                (bean.colorPalette as Map<String, Any?>).mapValues {
                    when (val value = it.value) {
                        is String -> value
                        else -> null
                    }
                }
            val keyPalette = UITheme.getColorPalette()
            val themeColors = bean.colors.mapValues { (_, v) -> Color(v) }

            return BridgePainterHintsProvider(isDark, keyPalette, iconColorPalette, themeColors)
        }
    }
}
