package org.jetbrains.jewel.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.modifiers.background
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDark
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.Surface
import org.jetbrains.jewel.theme.intellij.components.Text

fun main() {
    singleWindowApplication {
        IntelliJThemeDark {
            Box(modifier = Modifier.fillMaxSize()) {
                Column {
                    // TODO: set proportion
                    WizardHeader()
                    WizardMainContent(Modifier.weight(1f))
                    WizardFooter()
                }
            }
        }
    }
}

@Composable
fun WizardHeader(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Red).height(100.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource("imageasset/android-studio.svg"), "logo", modifier = Modifier.height(50.dp))
            Text("Configure Image Asset")
        }
    }
}

@Composable
fun WizardMainContent(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Green).fillMaxWidth()) {

    }
}

@Composable
fun WizardFooter(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Blue).height(50.dp).fillMaxWidth()) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HelpIcon()
            WizardControls()
        }
    }
}

@Composable
fun HelpIcon(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.White).size(24.dp))
}

@Composable
fun WizardControls(modifier: Modifier = Modifier) {
    Row {
        Button(onClick = {}) {
            Text("Cancel")
        }
        Button(onClick = {}) {
            Text("Previous")
        }
        Button(onClick = {}) {
            Text("Next")
        }
        Button(onClick = {}) {
            Text("Finish")
        }
    }
}