package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.Text
import org.jetbrains.jewel.TextArea

@Composable
fun ColumnScope.TextAreas() {
    GroupHeader("TextAreas")
    Row(
        Modifier.height(144.dp).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var text1 by remember {
            mutableStateOf(
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
            )
        }
        TextArea(text1, { text1 = it }, modifier = Modifier.weight(1f).fillMaxHeight())

        TextArea(text1, { text1 = it }, modifier = Modifier.weight(1f).fillMaxHeight(), isError = true)

        TextArea(text1, { text1 = it }, modifier = Modifier.weight(1f).fillMaxHeight(), enabled = false)

        var text4 by remember { mutableStateOf("") }
        TextArea(text4, { text4 = it }, hint = {
            Text("This is hint text")
        }, modifier = Modifier.weight(1f).fillMaxHeight(), placeholder = {
                Text("Placeholder")
            })
    }
}
