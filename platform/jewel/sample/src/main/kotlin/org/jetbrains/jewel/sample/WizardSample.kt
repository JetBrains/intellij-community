package org.jetbrains.jewel.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.singleWindowApplication
import org.jetbrains.jewel.theme.intellij.IntelliJThemeDark
import org.jetbrains.jewel.theme.intellij.components.Button
import org.jetbrains.jewel.theme.intellij.components.Text

private const val WIZARD_PAGE_COUNT = 2

fun main() {
    singleWindowApplication {
        IntelliJThemeDark {
            Box(modifier = Modifier.fillMaxSize()) {
                Column {
                    val currentPage = mutableStateOf(1) // 1-based
                    WizardHeader()
                    WizardMainContent(
                        modifier = Modifier.weight(1f),
                        currentPage = currentPage
                    )
                    WizardFooter(currentPage = currentPage)
                }
            }
        }
    }
}

@Composable
fun WizardHeader(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF616161)).height(100.dp).fillMaxWidth()) {
        Row(modifier = modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = modifier.height(74.dp).padding(10.dp),
                painter = painterResource("imageasset/android-studio.svg"),
                contentDescription = "logo",
                tint = Color.Unspecified // FIXME: tint is being applied regardless
            )
            Text(
                text = "Configure Image Asset",
                fontSize = 24.sp
            )
        }
    }
}

@Composable
fun WizardMainContent(modifier: Modifier = Modifier, currentPage: MutableState<Int>) {
    if (currentPage.value == 1) {
        Box(modifier.background(Color.Green).fillMaxWidth())
    }
    else if (currentPage.value == 2) {
        Box(modifier.background(Color.Yellow).fillMaxWidth())
    }
}

@Composable
fun WizardFooter(modifier: Modifier = Modifier, currentPage: MutableState<Int>) {
    Box(modifier.background(Color.Blue).height(50.dp).fillMaxWidth()) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HelpIcon()
            WizardControls(currentPage = currentPage)
        }
    }
}

@Composable
fun HelpIcon(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.White).size(24.dp))
}

@Composable
fun WizardControls(modifier: Modifier = Modifier, currentPage: MutableState<Int>) {
    Row {
        Button(onClick = {}) { // TODO: close application on cancel
            Text("Cancel")
        }
        Button(onClick = { currentPage.value-- }, enabled = currentPage.value > 1) {
            Text("Previous")
        }
        Button(onClick = { currentPage.value++ }, enabled = currentPage.value < WIZARD_PAGE_COUNT) {
            Text("Next")
        }
        Button(onClick = {}, enabled = currentPage.value == WIZARD_PAGE_COUNT) { // TODO: close application on finish
            Text("Finish")
        }
    }
}