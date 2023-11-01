package org.jetbrains.jewel.bridge

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.NewUI
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.jewel.bridge.theme.createBridgeComponentStyling
import org.jetbrains.jewel.bridge.theme.createBridgeThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import kotlin.time.Duration.Companion.milliseconds

@Service(Level.APP)
internal class SwingBridgeService : Disposable {

    private val logger = thisLogger()

    // TODO use constructor injection when min IJ is 232+
    private val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + CoroutineName("JewelSwingBridge"))

    internal val currentBridgeThemeData: StateFlow<BridgeThemeData> =
        IntelliJApplication.lookAndFeelChangedFlow(coroutineScope)
            .mapLatest { tryGettingThemeData() }
            .stateIn(coroutineScope, SharingStarted.Eagerly, BridgeThemeData.DEFAULT)

    private suspend fun tryGettingThemeData(): BridgeThemeData {
        var counter = 0
        while (counter < 20) {
            delay(20.milliseconds)
            counter++
            runCatching { readThemeData() }
                .onSuccess { return it }
        }
        return readThemeData()
    }

    private suspend fun readThemeData(): BridgeThemeData {
        val isIntUi = NewUI.isEnabled()
        if (!isIntUi) {
            // TODO return Darcula/IntelliJ Light theme instead
            logger.warn("Darcula LaFs (aka \"old UI\") are not supported yet, falling back to Int UI")
        }

        val themeDefinition = createBridgeThemeDefinition()
        return BridgeThemeData(
            themeDefinition = createBridgeThemeDefinition(),
            componentStyling = createBridgeComponentStyling(themeDefinition),
            density = retrieveDensity(),
        )
    }

    override fun dispose() {
        coroutineScope.cancel("Disposing Application...")
    }

    internal data class BridgeThemeData(
        val themeDefinition: ThemeDefinition,
        val componentStyling: ComponentStyling,
        val density: Density,
    ) {

        companion object {

            val DEFAULT = run {
                val themeDefinition = createBridgeThemeDefinition(TextStyle.Default)
                BridgeThemeData(
                    themeDefinition = createBridgeThemeDefinition(TextStyle.Default),
                    componentStyling = createBridgeComponentStyling(
                        theme = themeDefinition,
                        textFieldTextStyle = TextStyle.Default,
                        textAreaTextStyle = TextStyle.Default,
                        dropdownTextStyle = TextStyle.Default,
                        linkTextStyle = TextStyle.Default,
                    ),
                    density = retrieveDensity(),
                )
            }
        }
    }
}
