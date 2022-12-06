package org.jetbrains.jewel.themes.darcula.standalone

import androidx.compose.ui.res.painterResource
import org.jetbrains.jewel.IntelliJPainters

val IntelliJPainters.Companion.light
    get() = IntelliJPainters(
        checkbox = IntelliJPainters.CheckboxPainters.light,
        radioButton = IntelliJPainters.RadioButtonPainters.light,
        treeView = IntelliJPainters.TreeViewPainters.light
    )

val IntelliJPainters.Companion.darcula
    get() = IntelliJPainters(
        checkbox = IntelliJPainters.CheckboxPainters.darcula,
        radioButton = IntelliJPainters.RadioButtonPainters.darcula,
        treeView = IntelliJPainters.TreeViewPainters.darcula
    )

val IntelliJPainters.CheckboxPainters.Companion.light
    get() = IntelliJPainters.CheckboxPainters(
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

val IntelliJPainters.CheckboxPainters.Companion.darcula
    get() = IntelliJPainters.CheckboxPainters(
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

val IntelliJPainters.RadioButtonPainters.Companion.light
    get() = IntelliJPainters.RadioButtonPainters(
        unselected = { painterResource("intellij/radio.svg") },
        unselectedDisabled = { painterResource("intellij/radioDisabled.svg") },
        unselectedFocused = { painterResource("intellij/radioFocused.svg") },
        selected = { painterResource("intellij/radioSelected.svg") },
        selectedDisabled = { painterResource("intellij/radioSelectedDisabled.svg") },
        selectedFocused = { painterResource("intellij/radioSelectedFocused.svg") },
    )

val IntelliJPainters.RadioButtonPainters.Companion.darcula
    get() = IntelliJPainters.RadioButtonPainters(
        unselected = { painterResource("darcula/radio.svg") },
        unselectedDisabled = { painterResource("darcula/radioDisabled.svg") },
        unselectedFocused = { painterResource("darcula/radioFocused.svg") },
        selected = { painterResource("darcula/radioSelected.svg") },
        selectedDisabled = { painterResource("darcula/radioSelectedDisabled.svg") },
        selectedFocused = { painterResource("darcula/radioSelectedFocused.svg") },
    )

val IntelliJPainters.TreeViewPainters.Companion.light
    get() = IntelliJPainters.TreeViewPainters(
        arrow = { painterResource("intellij/chevron-right.svg") }
    )

val IntelliJPainters.TreeViewPainters.Companion.darcula
    get() = IntelliJPainters.TreeViewPainters(
        arrow = { painterResource("darcula/chevron-right.svg") }
    )
