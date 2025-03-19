package org.jetbrains.jewel.bridge

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.intellij.ide.ui.LafFlowService
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.jetbrains.jewel.bridge.theme.createBridgeComponentStyling
import org.jetbrains.jewel.bridge.theme.createBridgeThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.copyWithSize

@Suppress("UnstableApiUsage")
internal class SwingBridgeService {
    private val scrollbarHelper = ScrollbarHelper.getInstance()

    internal val currentBridgeThemeData: StateFlow<BridgeThemeData> =
        LafFlowService.getInstance().customLafFlowState(BridgeThemeData.DEFAULT) { flow ->
            combine(flow, scrollbarHelper.scrollbarVisibilityStyleFlow, scrollbarHelper.trackClickBehaviorFlow) {
                _,
                _,
                _ ->
                tryGettingThemeData()
            }
        }

    private suspend fun tryGettingThemeData(): BridgeThemeData {
        var counter = 0
        while (counter < 20) {
            delay(20.milliseconds)
            counter++
            runCatching { readThemeData() }
                .onSuccess {
                    return it
                }
        }
        return readThemeData()
    }

    private fun readThemeData(): BridgeThemeData {
        val themeDefinition = createBridgeThemeDefinition()
        return BridgeThemeData(
            themeDefinition = createBridgeThemeDefinition(),
            componentStyling = createBridgeComponentStyling(themeDefinition),
        )
    }

    internal data class BridgeThemeData(val themeDefinition: ThemeDefinition, val componentStyling: ComponentStyling) {
        companion object {
            val DEFAULT = run {
                val textStyle = TextStyle.Default.copyWithSize(fontSize = 13.sp)
                val monospaceTextStyle = textStyle.copy(fontFamily = FontFamily.Monospace)
                val themeDefinition =
                    createBridgeThemeDefinition(
                        textStyle = textStyle,
                        editorTextStyle = monospaceTextStyle,
                        consoleTextStyle = monospaceTextStyle,
                    )

                BridgeThemeData(
                    themeDefinition = themeDefinition,
                    componentStyling = createBridgeComponentStyling(themeDefinition),
                )
            }
        }
    }
}
