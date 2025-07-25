// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SplitLayoutState
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticalSplitLayout

@Composable
public fun SplitLayouts(
    outerSplitState: SplitLayoutState,
    verticalSplitState: SplitLayoutState,
    innerSplitState: SplitLayoutState,
    onResetState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reset split state:")
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onResetState) { Text("Reset") }
        }

        Spacer(Modifier.height(16.dp))

        HorizontalSplitLayout(
            state = outerSplitState,
            first = { FirstPane() },
            second = { SecondPane(innerSplitState = innerSplitState, verticalSplitState = verticalSplitState) },
            modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, color = JewelTheme.globalColors.borders.normal),
            firstPaneMinWidth = 300.dp,
            secondPaneMinWidth = 200.dp,
        )
    }
}

@Composable
private fun FirstPane() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        val state by remember { mutableStateOf(TextFieldState()) }
        TextField(state, placeholder = { Text("Placeholder") })
    }
}

@Composable
private fun SecondPane(innerSplitState: SplitLayoutState, verticalSplitState: SplitLayoutState) {
    VerticalSplitLayout(
        state = verticalSplitState,
        modifier = Modifier.fillMaxSize(),
        first = {
            val state by remember { mutableStateOf(TextFieldState()) }
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                TextField(state, placeholder = { Text("Right Panel Content") })
            }
        },
        second = {
            HorizontalSplitLayout(
                first = {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Second Pane left")
                    }
                },
                second = {
                    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Second Pane right")
                    }
                },
                modifier = Modifier.fillMaxSize(),
                state = innerSplitState,
                firstPaneMinWidth = 100.dp,
                secondPaneMinWidth = 100.dp,
            )
        },
        firstPaneMinWidth = 300.dp,
        secondPaneMinWidth = 100.dp,
    )
}
