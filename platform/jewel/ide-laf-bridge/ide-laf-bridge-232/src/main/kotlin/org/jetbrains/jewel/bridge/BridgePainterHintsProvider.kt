package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.intellij.ide.ui.UITheme
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.jewel.InternalJewelApi
import org.jetbrains.jewel.intui.core.IntUiPainterHintsProvider
import org.jetbrains.jewel.painter.PainterHint
import org.jetbrains.jewel.util.fromRGBAHexString

@InternalJewelApi
class BridgePainterHintsProvider private constructor(
    isDark: Boolean,
    intellijIconPalette: Map<String, String> = emptyMap(),
    themeIconPalette: Map<String, String?> = emptyMap(),
    themeColorPalette: Map<String, Color?> = emptyMap(),
) : IntUiPainterHintsProvider(
    isDark,
    intellijIconPalette,
    themeIconPalette,
    themeColorPalette,
) {

    @Composable
    override fun hints(path: String): List<PainterHint> = buildList {
        add(getPaletteHint(path))
        add(BridgeOverride)
    }

    companion object {

        private val logger = thisLogger()

        operator fun invoke(isDark: Boolean): IntUiPainterHintsProvider {
            val uiTheme = currentUiThemeOrNull() ?: return BridgePainterHintsProvider(isDark)
            logger.info("Parsing theme info from theme ${uiTheme.name} (id: ${uiTheme.id}, isDark: ${uiTheme.isDark})")

            val iconColorPalette = uiTheme.iconColorPalette
            val keyPalette = UITheme.getColorPalette()
            val themeColors = uiTheme.colors.orEmpty().mapValues { (k, v) ->
                when (v) {
                    is Int -> Color(v)
                    is String -> Color.fromRGBAHexString(v)
                    else -> null
                }
            }

            return BridgePainterHintsProvider(isDark, keyPalette, iconColorPalette, themeColors)
        }
    }
}
