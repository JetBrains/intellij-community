// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.OnOffButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/** Showcases the [OnOffButton] component. */
@Composable
public fun OnOffButtons(modifier: Modifier = Modifier) {
    VerticallyScrollableContainer(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var toggled by remember { mutableStateOf(true) }

                Text("On: ")

                OnOffButton(checked = toggled, onCheckedChange = { toggled = !toggled }, enabled = true)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var toggled by remember { mutableStateOf(false) }

                Text("Off: ")

                OnOffButton(checked = toggled, onCheckedChange = { toggled = !toggled }, enabled = true)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("On Disabled: ")

                OnOffButton(checked = true, onCheckedChange = {}, enabled = false)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Off Disabled: ")

                OnOffButton(checked = false, onCheckedChange = {}, enabled = false)
            }
        }
    }
}
