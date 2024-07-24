package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.component.ActionButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.IconActionButton
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.PlatformIcon
import org.jetbrains.jewel.ui.component.SelectableIconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.styling.LocalIconButtonStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.hints.Stroke

@Composable
@View(title = "Buttons", position = 0, icon = "icons/components/button.svg")
fun Buttons() {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NormalButtons()
        IconButtons()
        IconActionButtons()
        ActionButtons()
    }
}

@Composable
private fun NormalButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { }) {
            Text("Outlined")
        }

        OutlinedButton(onClick = {}, enabled = false) {
            Text("Outlined Disabled")
        }

        DefaultButton(onClick = {}) {
            Text("Default")
        }

        DefaultButton(onClick = {}, enabled = false) {
            Text("Default disabled")
        }
    }
}

@Composable
private fun IconButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("IconButton", style = Typography.h4TextStyle())

        Text("Focusable:")

        IconButton(onClick = {}) {
            PlatformIcon(AllIconsKeys.Actions.Close, contentDescription = "IconButton")
        }

        Text("Not focusable:")

        IconButton(onClick = {}, focusable = false) {
            PlatformIcon(AllIconsKeys.Actions.Close, contentDescription = "IconButton")
        }

        Text("Selectable:")

        var selected by remember { mutableStateOf(false) }
        SelectableIconButton(onClick = { selected = !selected }, selected = selected) { state ->
            val tint by LocalIconButtonStyle.current.colors.foregroundFor(state)
            PlatformIcon(
                key = AllIconsKeys.Actions.MatchCase,
                contentDescription = "IconButton",
                hints = arrayOf(Selected(selected), Stroke(tint)),
            )
        }
    }
}

@Composable
private fun IconActionButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("IconActionButton", style = Typography.h4TextStyle())

        Text("With tooltip:")

        IconActionButton(key = AllIconsKeys.Actions.Copy, contentDescription = "IconButton", onClick = {}) {
            Text("I am a tooltip")
        }

        Text("Without tooltip:")

        IconActionButton(key = AllIconsKeys.Actions.Copy, contentDescription = "IconButton", onClick = {})
    }
}

@Composable
private fun ActionButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ActionButton", style = Typography.h4TextStyle())

        Text("With tooltip:")

        ActionButton(onClick = {}, tooltip = { Text("I am a tooltip") }) {
            Text("Hover me!")
        }

        Text("Without tooltip:")

        ActionButton(onClick = {}) {
            Text("Do something")
        }
    }
}
