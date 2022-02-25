package org.jetbrains.jewel.theme.idea

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.Checkbox
import org.jetbrains.jewel.theme.intellij.components.CheckboxRow
import org.jetbrains.jewel.theme.intellij.components.Text

internal class ProjectLifecycle : Disposable, CoroutineScope {

    override val coroutineContext = SupervisorJob()

    override fun dispose() = cancel()
}

@ExperimentalCoroutinesApi
internal class JewelDemoToolWindow : ToolWindowFactory, DumbAware {

    enum class RadioSample {
        Enabled, Disabled, Automatic, Unavailable
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposeTab("Compose Demo") {
            IntelliJTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(org.jetbrains.jewel.theme.intellij.IntelliJTheme.palette.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp, alignment = Alignment.CenterVertically)) {
                        var clicks by remember { mutableStateOf(0) }
                        Button({ clicks++ }) {
                            Text("Hello world, $clicks")
                        }

                        var checked by remember { mutableStateOf(false) }

                        CheckboxRow(
                            checked = checked,
                            onCheckedChange = { checked = it }
                        ) {
                            Text("Hello, I am a themed checkbox")
                        }

                        val textFieldState = remember { mutableStateOf("I am a textfield") }
//                        TextField(textFieldState.value, { textFieldState.value = it })

                        val radioState = remember { mutableStateOf(RadioSample.Automatic) }
                        Column(
                            Modifier.selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(org.jetbrains.jewel.theme.intellij.IntelliJTheme.metrics.singlePadding)
                        ) {
//                            RadioButtonRow(radioState, RadioSample.Automatic) {
//                                Text("Automatic detection of the property", Modifier.alignByBaseline())
//                            }
//                            RadioButtonRow(radioState, RadioSample.Enabled) {
//                                Text("Enable the property", Modifier.alignByBaseline())
//                            }
//                            RadioButtonRow(radioState, RadioSample.Disabled) {
//                                Text("Disable the property", Modifier.alignByBaseline())
//                            }
//                            RadioButtonRow(radioState, RadioSample.Unavailable, enabled = false) {
//                                Text("Unavailable", Modifier.alignByBaseline())
//                            }
                        }
                    }
                }
            }
        }
        toolWindow.addComposeTab("Compose Demo 2") {
            IntelliJTheme {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    var checked by remember { mutableStateOf(true) }
                    Column {
                        Button({}) {
                            Text("Hello world 2")
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { checked = it }
                        )
                    }
                }
            }
        }
    }
}

internal fun ToolWindow.addComposeTab(
    displayName: String,
    isLockable: Boolean = true,
    content: @Composable () -> Unit
) = ComposePanel(content = content)
    .also { contentManager.addContent(contentManager.factory.createContent(it, displayName, isLockable)) }

internal fun ComposePanel(
    height: Int = 800,
    width: Int = 800,
    y: Int = 0,
    x: Int = 0,
    content: @Composable () -> Unit
): ComposePanel {
    val panel = ComposePanel()
    panel.setBounds(x, y, width, height)
    panel.setContent(content)
    return panel
}