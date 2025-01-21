package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.samples.showcase.components.ShowcaseIcons
import org.jetbrains.jewel.samples.showcase.views.ComponentsView
import org.jetbrains.jewel.samples.showcase.views.ComponentsViewModel
import org.jetbrains.jewel.samples.showcase.views.KeyBinding
import org.jetbrains.jewel.samples.showcase.views.ViewInfo
import org.jetbrains.jewel.samples.standalone.IntUiThemes
import org.jetbrains.jewel.samples.standalone.view.MarkdownDemo
import org.jetbrains.jewel.samples.standalone.view.WelcomeView
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel.componentsViewModel
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility

@ExperimentalLayoutApi
object MainViewModel {
    val componentsViewModel: ComponentsViewModel
        get() {
            val alwaysVisibleScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default()
            val whenScrollingScrollbarVisibility = ScrollbarVisibility.WhenScrolling.default()
            return ComponentsViewModel(
                alwaysVisibleScrollbarVisibility = alwaysVisibleScrollbarVisibility,
                whenScrollingScrollbarVisibility = whenScrollingScrollbarVisibility,
            )
        }

    fun onNavigateTo(destination: String) {
        currentView = views.first { viewInfo -> viewInfo.title == destination }
    }

    var theme: IntUiThemes by mutableStateOf(IntUiThemes.Light)

    var swingCompat: Boolean by mutableStateOf(false)

    val projectColor
        get() =
            if (theme.isLightHeader()) {
                Color(0xFFF5D4C1)
            } else {
                Color(0xFF654B40)
            }

    val views = mainMenuItems

    var currentView by mutableStateOf(views.first())
}

@OptIn(ExperimentalLayoutApi::class)
private val mainMenuItems =
    mutableStateListOf(
        ViewInfo(
            title = "Welcome",
            iconKey = ShowcaseIcons.welcome,
            keyboardShortcut = KeyBinding(macOs = setOf("⌥", "W"), windows = setOf("Alt", "W")),
            content = { WelcomeView() },
        ),
        ViewInfo(
            title = "Components",
            iconKey = ShowcaseIcons.componentsMenu,
            keyboardShortcut = KeyBinding(macOs = setOf("⌥", "C"), windows = setOf("Alt", "C")),
            content = { ComponentsView(viewModel = componentsViewModel, Modifier.size(40.dp).padding(4.dp)) },
        ),
        ViewInfo(
            title = "Markdown",
            iconKey = ShowcaseIcons.markdown,
            keyboardShortcut =
                KeyBinding(macOs = setOf("⌥", "M"), windows = setOf("Alt", "M"), linux = setOf("Alt", "M")),
            content = { MarkdownDemo() },
        ),
    )
