package org.jetbrains.jewel.theme.idea.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.theme.idea.IntelliJTheme
import org.jetbrains.jewel.theme.idea.addComposePanel
import org.jetbrains.jewel.theme.intellij.IntelliJTheme
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.Checkbox
import org.jetbrains.jewel.theme.intellij.components.CheckboxRow
import org.jetbrains.jewel.theme.intellij.components.Divider
import org.jetbrains.jewel.theme.intellij.components.RadioButtonRow
import org.jetbrains.jewel.theme.intellij.components.Tab
import org.jetbrains.jewel.theme.intellij.components.TabRow
import org.jetbrains.jewel.theme.intellij.components.TabScope
import org.jetbrains.jewel.theme.intellij.components.Text
import org.jetbrains.jewel.theme.intellij.components.TextField
import org.jetbrains.jewel.theme.intellij.components.rememberTabContainerState

@ExperimentalCoroutinesApi
internal class JewelDemoToolWindow : ToolWindowFactory, DumbAware {

    enum class RadioSample {
        Enabled, Disabled, Automatic, Unavailable
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposePanel("Compose Demo") {
            IntelliJTheme(this) {
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

                        var textFieldValue by remember { mutableStateOf("") }
                        TextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                            }
                        )

                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text("One")
                            Divider(orientation = Orientation.Vertical, startIndent = 12.dp)
                            Text("Two")
                            Divider(orientation = Orientation.Vertical)
                            Text("Three")
                            Divider(orientation = Orientation.Vertical)
                            Text("Four")
                        }

                        CheckboxRow(
                            checked = checked,
                            onCheckedChange = { checked = it }
                        ) {
                            Text("Hello, I am a themed checkbox")
                        }

                        val tabState = rememberTabContainerState("1")
                        TabRow(tabState) {
                            Section("1", "One")
                            Section("2", "Two")
                            Section("3", "Three")
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            when (tabState.selectedKey) {
                                "1" -> Text("Content of One")
                                "2" -> Text("Content of Two")
                                "3" -> Text("Content of Three")
                            }
                        }

                        var radioState by remember { mutableStateOf(RadioSample.Automatic) }
                        Column(
                            Modifier.selectableGroup(),
                            verticalArrangement = Arrangement.spacedBy(IntelliJTheme.metrics.singlePadding)
                        ) {
                            RadioButtonRow(
                                selected = radioState == RadioSample.Automatic,
                                onClick = { radioState = RadioSample.Automatic }
                            ) {
                                Text("Automatic detection of the property", Modifier.alignByBaseline())
                            }
                            RadioButtonRow(
                                selected = radioState == RadioSample.Enabled,
                                onClick = { radioState = RadioSample.Enabled }
                            ) {
                                Text("Enable the property", Modifier.alignByBaseline())
                            }
                            RadioButtonRow(
                                selected = radioState == RadioSample.Disabled,
                                onClick = { radioState = RadioSample.Disabled }
                            ) {
                                Text("Disable the property", Modifier.alignByBaseline())
                            }
                            RadioButtonRow(
                                selected = radioState == RadioSample.Unavailable,
                                onClick = { radioState = RadioSample.Unavailable },
                                enabled = false
                            ) {
                                Text("Unavailable", Modifier.alignByBaseline())
                            }
                        }
                    }
                }
            }
        }
        toolWindow.addComposePanel("Compose Demo 2") {
            IntelliJTheme(this) {
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

@Composable
private fun TabScope<String>.Section(key: String, caption: String) {
    Tab(key) {
        Text(caption)
    }
}