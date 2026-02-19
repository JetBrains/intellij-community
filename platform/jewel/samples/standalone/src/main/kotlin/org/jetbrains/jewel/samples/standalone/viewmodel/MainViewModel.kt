package org.jetbrains.jewel.samples.standalone.viewmodel

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.styling.default
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.samples.showcase.ShowcaseIcons
import org.jetbrains.jewel.samples.showcase.views.ComponentsView
import org.jetbrains.jewel.samples.showcase.views.ComponentsViewModel
import org.jetbrains.jewel.samples.showcase.views.KeyBinding
import org.jetbrains.jewel.samples.showcase.views.ViewInfo
import org.jetbrains.jewel.samples.standalone.IntUiThemes
import org.jetbrains.jewel.samples.standalone.view.MarkdownDemo
import org.jetbrains.jewel.samples.standalone.view.WelcomeView
import org.jetbrains.jewel.samples.standalone.viewmodel.MainViewModel.componentsViewModel
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility

public object MainViewModel {
    public val componentsViewModel: ComponentsViewModel

    init {
        val alwaysVisibleScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default()
        val whenScrollingScrollbarVisibility = ScrollbarVisibility.WhenScrolling.default()
        componentsViewModel =
            ComponentsViewModel(
                alwaysVisibleScrollbarVisibility = alwaysVisibleScrollbarVisibility,
                whenScrollingScrollbarVisibility = whenScrollingScrollbarVisibility,
            )
    }

    public fun onNavigateTo(destination: String) {
        currentView = views.first { viewInfo: ViewInfo -> viewInfo.title == destination }
    }

    public var theme: IntUiThemes by mutableStateOf(IntUiThemes.Light)

    public var swingCompat: Boolean by mutableStateOf(false)

    public var useCustomPopupRenderer: Boolean by mutableStateOf(JewelFlags.useCustomPopupRenderer)

    public val projectColor: Color
        get() =
            if (theme.isLightHeader()) {
                Color(0xFFF5D4C1)
            } else {
                Color(0xFF654B40)
            }

    public val views: SnapshotStateList<ViewInfo> = mainMenuItems

    public var currentView: ViewInfo by mutableStateOf(views.first())
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
            content = {
                ComponentsView(
                    viewModel = componentsViewModel,
                    // See JBUI.CurrentTheme.Toolbar.stripeToolbarButton* defaults
                    toolbarButtonMetrics =
                        IconButtonMetrics.defaults(
                            cornerSize = CornerSize(6.dp),
                            padding = PaddingValues(5.dp),
                            minSize = DpSize(40.dp, 40.dp),
                        ),
                )
            },
        ),
        ViewInfo(
            title = "Markdown",
            iconKey = ShowcaseIcons.markdown,
            keyboardShortcut =
                KeyBinding(macOs = setOf("⌥", "M"), windows = setOf("Alt", "M"), linux = setOf("Alt", "M")),
            content = { MarkdownDemo() },
        ),
    )
