@file:Suppress("MagicNumber")

package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.TabStrip

@Composable
fun Tabs() {
    GroupHeader("Tabs")
    TabStrip(modifier = Modifier.fillMaxWidth()) {
        repeat(3) {
            tab(
                selected = true,
                label = "Default Tab 1",
                closable = true
            )
            tab(
                selected = false,
                label = "Default Tab 2",
                closable = true
            )
            tab(
                selected = true,
                label = "Default Tab 3",
                closable = false
            )
            tab(
                selected = false,
                label = "Default Tab 4",
                closable = false
            )
            editorTab(
                selected = true,
                label = "Editor Tab 1",
                closable = true
            )
            editorTab(
                selected = false,
                label = "Editor Tab 2",
                closable = true
            )
            editorTab(
                selected = true,
                label = "Editor Tab 1",
                closable = false
            )
            editorTab(
                selected = false,
                label = "Editor Tab 2",
                closable = false
            )
        }
    }
}
