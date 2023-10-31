package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.runtime.Composable

data class ViewInfo(
    val title: String,
    val position: Int,
    val icon: String,
    val content: @Composable () -> Unit,
)

@Target(AnnotationTarget.FUNCTION)
annotation class View(
    val title: String,
    val position: Int = 0,
    val icon: String = "icons/stub.svg",
)
