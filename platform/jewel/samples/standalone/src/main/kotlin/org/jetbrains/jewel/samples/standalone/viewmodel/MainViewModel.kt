package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.samples.standalone.IntUiThemes
import org.jetbrains.jewel.samples.standalone.reflection.findViews

object MainViewModel {
    var theme: IntUiThemes by mutableStateOf(IntUiThemes.Light)

    var swingCompat: Boolean by mutableStateOf(false)

    val projectColor
        get() =
            if (theme.isLightHeader()) {
                Color(0xFFF5D4C1)
            } else {
                Color(0xFF654B40)
            }

    val views = findViews("org.jetbrains.jewel.samples.standalone.view").toMutableStateList()

    var currentView by mutableStateOf(views.first())
}
