package org.jetbrains.jewel.bridge

import androidx.compose.ui.text.TextStyle
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.NewUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.jewel.IntelliJComponentStyling
import org.jetbrains.jewel.IntelliJSvgLoader
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.themes.PaletteMapperFactory
import org.jetbrains.jewel.themes.intui.core.IntUiThemeDefinition
import org.jetbrains.jewel.themes.intui.core.IntelliJSvgPatcher

@Service(Level.APP)
class SwingBridgeService : Disposable {

    private val logger = thisLogger()

    // TODO use constructor injection when min IJ is 232+
    private val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + CoroutineName("JewelSwingBridge"))

    // TODO we shouldn't assume it's Int UI, but we only have that for now
    internal val currentBridgeThemeData: StateFlow<BridgeThemeData> =
        IntelliJApplication.lookAndFeelChangedFlow(coroutineScope)
            .map { readThemeData() }
            .stateIn(coroutineScope, SharingStarted.Eagerly, BridgeThemeData.DEFAULT)

    private suspend fun readThemeData(): BridgeThemeData {
        val isIntUi = NewUI.isEnabled()
        if (!isIntUi) {
            // TODO return Darcula/IntelliJ Light theme instead
            logger.warn("Darcula LaFs (aka \"old UI\") are not supported yet, falling back to Int UI")
        }

        val themeDefinition = createBridgeIntUiDefinition()
        val svgLoader = createSvgLoader(themeDefinition)
        return BridgeThemeData(
            themeDefinition = createBridgeIntUiDefinition(),
            svgLoader = svgLoader,
            componentStyling = createSwingIntUiComponentStyling(themeDefinition, svgLoader),
        )
    }

    override fun dispose() {
        coroutineScope.cancel("Disposing Application...")
    }

    internal data class BridgeThemeData(
        val themeDefinition: IntUiThemeDefinition,
        val svgLoader: SvgLoader,
        val componentStyling: IntelliJComponentStyling,
    ) {

        companion object {

            val DEFAULT = run {
                val themeDefinition = createBridgeIntUiDefinition(TextStyle.Default)
                val svgLoader = createSvgLoader(themeDefinition)
                BridgeThemeData(
                    themeDefinition = createBridgeIntUiDefinition(TextStyle.Default),
                    svgLoader = createSvgLoader(themeDefinition),
                    componentStyling = createSwingIntUiComponentStyling(
                        theme = themeDefinition,
                        svgLoader = svgLoader,
                        textAreaTextStyle = TextStyle.Default,
                        textFieldTextStyle = TextStyle.Default,
                        dropdownTextStyle = TextStyle.Default,
                        labelTextStyle = TextStyle.Default,
                        linkTextStyle = TextStyle.Default,
                    ),
                )
            }
        }
    }
}

private fun createSvgLoader(theme: IntUiThemeDefinition): SvgLoader {
    val paletteMapper = PaletteMapperFactory.create(theme.isDark, theme.iconData, theme.colorPalette)
    val svgPatcher = IntelliJSvgPatcher(paletteMapper)
    return IntelliJSvgLoader(svgPatcher)
}
