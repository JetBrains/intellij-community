package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

@Suppress("SpellCheckingInspection")
private const val LOREM_IPSUM =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n" +
        "Sed auctor, neque in accumsan vehicula, enim purus vestibulum odio, non tristique dolor quam vel ipsum. \n" +
        "Proin egestas, orci id hendrerit bibendum, nisl neque imperdiet nisl, a euismod nibh diam nec lectus. \n" +
        "Duis euismod, quam nec aliquam iaculis, dolor lorem bibendum turpis, vel malesuada augue sapien vel mi. \n" +
        "Quisque ut facilisis nibh. Maecenas euismod hendrerit sem, ac scelerisque odio auctor nec. \n" +
        "Sed sit amet consequat eros. Donec nisl tellus, accumsan nec ligula in, eleifend sodales sem. \n" +
        "Sed malesuada, nulla ac eleifend fermentum, nibh mi consequat quam, quis convallis lacus nunc eu dui. \n" +
        "Pellentesque eget enim quis orci porttitor consequat sed sed quam. \n" +
        "Sed aliquam, nisl et lacinia lacinia, diam nunc laoreet nisi, sit amet consectetur dolor lorem et sem. \n" +
        "Duis ultricies, mauris in aliquam interdum, orci nulla finibus massa, a tristique urna sapien vel quam. \n" +
        "Sed nec sapien nec dui rhoncus bibendum. Sed blandit bibendum libero."

@Composable
public fun TextAreas() {
    VerticallyScrollableContainer(Modifier.fillMaxSize()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp).height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TextArea(state = rememberTextFieldState(LOREM_IPSUM), modifier = Modifier.weight(1f).fillMaxHeight())

                TextArea(
                    state = rememberTextFieldState(LOREM_IPSUM),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enabled = false,
                )

                TextArea(
                    state = rememberTextFieldState(""),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    outline = Outline.Error,
                    placeholder = { Text("Text area with error") },
                )

                TextArea(
                    state = rememberTextFieldState(""),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    outline = Outline.Warning,
                    placeholder = { Text("Text area with warning") },
                )

                TextArea(
                    state = rememberTextFieldState(""),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    placeholder = { Text("Text area without decoration") },
                    undecorated = true,
                )
            }

            Spacer(Modifier.height(16.dp))

            GroupHeader("Read-only")

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.padding(horizontal = 16.dp).height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TextArea(
                    state = rememberTextFieldState(LOREM_IPSUM),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    readOnly = true,
                )

                TextArea(
                    state = rememberTextFieldState(LOREM_IPSUM),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    enabled = false,
                    readOnly = true,
                )

                TextArea(
                    state = rememberTextFieldState("Error state"),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    outline = Outline.Error,
                    placeholder = { Text("Text area with error") },
                    readOnly = true,
                )

                TextArea(
                    state = rememberTextFieldState("Warning state"),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    outline = Outline.Warning,
                    placeholder = { Text("Text area with warning") },
                    readOnly = true,
                )

                TextArea(
                    state = rememberTextFieldState(""),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    placeholder = { Text("Text area without decoration") },
                    undecorated = true,
                )
            }
        }
    }
}
