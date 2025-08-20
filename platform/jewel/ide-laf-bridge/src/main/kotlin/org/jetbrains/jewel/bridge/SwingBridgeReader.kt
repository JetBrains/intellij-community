package org.jetbrains.jewel.bridge

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.intellij.ide.ui.LafFlowService
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.messages.impl.subscribeAsFlow
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import org.jetbrains.jewel.bridge.theme.createBridgeComponentStyling
import org.jetbrains.jewel.bridge.theme.createBridgeThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling

@Suppress("UnstableApiUsage")
internal class SwingBridgeReader {
    private val scrollbarHelper = ScrollbarHelper.getInstance()
    private val settingsFlow: Flow<UISettings> =
        ApplicationManager.getApplication()
            .messageBus
            .subscribeAsFlow(UISettingsListener.TOPIC) { UISettingsListener { uiSettings -> trySend(uiSettings) } }
            .onStart { emit(UISettings.getInstance()) }

    private val editorSchemeChangeFlow: Flow<Unit> =
        ApplicationManager.getApplication()
            .messageBus
            .subscribeAsFlow(EditorColorsManager.TOPIC) { EditorColorsListener { trySend(Unit) } }
            .onStart { emit(Unit) }

    val currentBridgeThemeData: StateFlow<BridgeThemeData> =
        LafFlowService.getInstance().customLafFlowState(BridgeThemeData.DEFAULT) { lafChangeFlow ->
            combine(
                lafChangeFlow,
                scrollbarHelper.scrollbarVisibilityStyleFlow,
                scrollbarHelper.trackClickBehaviorFlow,
                settingsFlow,
                editorSchemeChangeFlow,
            ) { _, _, _, _, _ ->
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
            themeDefinition = themeDefinition,
            componentStyling = createBridgeComponentStyling(themeDefinition),
        )
    }

    internal data class BridgeThemeData(val themeDefinition: ThemeDefinition, val componentStyling: ComponentStyling) {
        companion object Companion {
            val DEFAULT = run {
                // Note that the line height on this textStyle is not properly computed, but it's fine as this default
                // value is only a placeholder that should only be used before the first composition.
                val textStyle = TextStyle.Default.copy(fontSize = 13.sp)
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
