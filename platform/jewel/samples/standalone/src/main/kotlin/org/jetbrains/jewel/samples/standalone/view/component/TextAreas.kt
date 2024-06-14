package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.layout.Arrangement
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
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea

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
@View(title = "TextAreas", position = 8, icon = "icons/components/textArea.svg")
fun TextAreas() {
    Row(
        Modifier.padding(horizontal = 16.dp).height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        var text1 by remember { mutableStateOf(LOREM_IPSUM) }
        TextArea(text1, { text1 = it }, modifier = Modifier.weight(1f).fillMaxHeight())

        var text2 by remember { mutableStateOf(LOREM_IPSUM) }
        TextArea(text2, { text2 = it }, modifier = Modifier.weight(1f).fillMaxHeight(), enabled = false)

        var text3 by remember { mutableStateOf("") }
        TextArea(
            text3,
            { text3 = it },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            outline = Outline.Error,
            placeholder = { Text("Text area with error") },
        )

        var text4 by remember { mutableStateOf("") }
        TextArea(
            text4,
            { text4 = it },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            outline = Outline.Warning,
            placeholder = { Text("Text area with warning") },
        )
    }
}
