package org.jetbrains.jewel.samples.ideplugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.intellij.ui.JBColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.jewel.CheckboxRow
import org.jetbrains.jewel.DefaultButton
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.OutlinedButton
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.TextField
import org.jetbrains.jewel.bridge.SwingBridgeTheme
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

@ExperimentalCoroutinesApi
internal class JewelDemoToolWindow : ToolWindowFactory, DumbAware {

    @OptIn(ExperimentalJewelApi::class)
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.addComposeTab("Jewel") {
            SwingBridgeTheme {
                val resourceLoader = LocalResourceLoader.current
                val bgColor by remember(IntUiTheme.isDark) { mutableStateOf(JBColor.PanelBackground.toComposeColor()) }

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Here is a selection of our finest components:")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        var clicks1 by remember { mutableStateOf(0) }
                        OutlinedButton({ clicks1++ }) {
                            Text("Outlined: $clicks1")
                        }
                        OutlinedButton({ }, enabled = false) {
                            Text("Outlined")
                        }

                        var clicks2 by remember { mutableStateOf(0) }
                        DefaultButton({ clicks2++ }) {
                            Text("Default: $clicks2")
                        }
                        DefaultButton({ }, enabled = false) {
                            Text("Default")
                        }
                    }

                    var textFieldValue by remember { mutableStateOf("") }
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        placeholder = { Text("Write something...") },
                        modifier = Modifier.width(200.dp),
                    )

                    var checked by remember { mutableStateOf(false) }
                    CheckboxRow(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        resourceLoader = resourceLoader,
                    ) {
                        Text("Hello, I am a themed checkbox")
                    }
                }
            }
        }
    }
}
