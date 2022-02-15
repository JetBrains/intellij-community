package org.jetbrains.jewel.theme.intellij

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource

class IntelliJPainters(
    val checkbox: CheckboxPainters,
    val radioButton: RadioButtonPainters,
) {

    companion object {

        val light = IntelliJPainters(
            checkbox = CheckboxPainters.light,
            radioButton = RadioButtonPainters.light
        )
        val darcula = IntelliJPainters(
            checkbox = CheckboxPainters.dark,
            radioButton = RadioButtonPainters.dark
        )
    }

    data class CheckboxPainters(
        val unselected: (@Composable () -> Painter),
        val unselectedDisabled: (@Composable () -> Painter),
        val unselectedFocused: (@Composable () -> Painter),
        val selected: (@Composable () -> Painter),
        val selectedDisabled: (@Composable () -> Painter),
        val selectedFocused: (@Composable () -> Painter),
        val indeterminate: (@Composable () -> Painter),
        val indeterminateDisabled: (@Composable () -> Painter),
        val indeterminateFocused: (@Composable () -> Painter),
    ) {

        companion object {

            val light = CheckboxPainters(
                unselected = { painterResource("intellij/checkBox.svg") },
                unselectedDisabled = { painterResource("intellij/checkBoxDisabled.svg") },
                unselectedFocused = { painterResource("intellij/checkBoxFocused.svg") },
                selected = { painterResource("intellij/checkBoxSelected.svg") },
                selectedDisabled = { painterResource("intellij/checkBoxSelectedDisabled.svg") },
                selectedFocused = { painterResource("intellij/checkBoxSelectedFocused.svg") },
                indeterminate = { painterResource("intellij/checkBoxIndeterminateSelected.svg") },
                indeterminateDisabled = { painterResource("intellij/checkBoxIndeterminateSelectedDisabled.svg") },
                indeterminateFocused = { painterResource("intellij/checkBoxIndeterminateSelectedFocused.svg") },
            )
            val dark = CheckboxPainters(
                unselected = { painterResource("darcula/checkBox.svg") },
                unselectedDisabled = { painterResource("darcula/checkBoxDisabled.svg") },
                unselectedFocused = { painterResource("darcula/checkBoxFocused.svg") },
                selected = { painterResource("darcula/checkBoxSelected.svg") },
                selectedDisabled = { painterResource("darcula/checkBoxSelectedDisabled.svg") },
                selectedFocused = { painterResource("darcula/checkBoxSelectedFocused.svg") },
                indeterminate = { painterResource("darcula/checkBoxIndeterminateSelected.svg") },
                indeterminateDisabled = { painterResource("darcula/checkBoxIndeterminateSelectedDisabled.svg") },
                indeterminateFocused = { painterResource("darcula/checkBoxIndeterminateSelectedFocused.svg") },
            )
        }
    }

    data class RadioButtonPainters(
        val unselected: (@Composable () -> Painter),
        val unselectedDisabled: (@Composable () -> Painter),
        val unselectedFocused: (@Composable () -> Painter),
        val selected: (@Composable () -> Painter),
        val selectedDisabled: (@Composable () -> Painter),
        val selectedFocused: (@Composable () -> Painter),
    ) {

        companion object {

            val light = RadioButtonPainters(
                unselected = { painterResource("intellij/radio.svg") },
                unselectedDisabled = { painterResource("intellij/radioDisabled.svg") },
                unselectedFocused = { painterResource("intellij/radioFocused.svg") },
                selected = { painterResource("intellij/radioSelected.svg") },
                selectedDisabled = { painterResource("intellij/radioSelectedDisabled.svg") },
                selectedFocused = { painterResource("intellij/radioSelectedFocused.svg") },
            )
            val dark = RadioButtonPainters(
                unselected = { painterResource("darcula/radio.svg") },
                unselectedDisabled = { painterResource("darcula/radioDisabled.svg") },
                unselectedFocused = { painterResource("darcula/radioFocused.svg") },
                selected = { painterResource("darcula/radioSelected.svg") },
                selectedDisabled = { painterResource("darcula/radioSelectedDisabled.svg") },
                selectedFocused = { painterResource("darcula/radioSelectedFocused.svg") },
            )
        }
    }
}
