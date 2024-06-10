package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import org.jetbrains.jewel.samples.standalone.reflection.findViews

object ComponentsViewModel {
    val views = findViews("org.jetbrains.jewel.samples.standalone.view.component").toMutableStateList()

    var currentView by mutableStateOf(views.first())
}
