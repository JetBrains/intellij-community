package org.jetbrains.jewel.theme.idea.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.theme.idea.IntelliJTheme
import org.jetbrains.jewel.theme.idea.addComposePanel
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.Checkbox
import org.jetbrains.jewel.theme.intellij.components.CheckboxRow
import org.jetbrains.jewel.theme.intellij.components.Text

@ExperimentalCoroutinesApi
internal class JewelDemoToolWindow : ToolWindowFactory, DumbAware {

    enum class RadioSample {
        Enabled, Disabled, Automatic, Unavailable
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposePanel("Compose Demo") {
            IntelliJTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(IntelliJTheme.palette.background),
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
                            verticalArrangement = Arrangement.spacedBy(IntelliJTheme.metrics.singlePadding)
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
        toolWindow.addComposePanel("Compose Demo 2") {
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
