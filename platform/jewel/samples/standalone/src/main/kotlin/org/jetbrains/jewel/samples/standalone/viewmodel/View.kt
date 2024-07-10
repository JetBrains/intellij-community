package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.ui.icon.IconKey

data class ViewInfo(
    val title: String,
    val position: Int,
    val iconKey: IconKey,
    val content: @Composable () -> Unit,
)

@Target(AnnotationTarget.FUNCTION)
annotation class View(
    val title: String,
    val position: Int = 0,
    val icon: String = "icons/stub.svg",
)
