package org.jetbrains.jewel.samples.ideplugin.dialog

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.toolWindow.ResizeStripeManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.Toolbar
import java.awt.Dimension
import javax.swing.JComponent
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeOrDefault
import org.jetbrains.jewel.bridge.theme.default
import org.jetbrains.jewel.bridge.theme.macOs
import org.jetbrains.jewel.bridge.toDpSize
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.intui.markdown.bridge.ProvideMarkdownStyling
import org.jetbrains.jewel.samples.showcase.views.ComponentsView
import org.jetbrains.jewel.samples.showcase.views.ComponentsViewModel
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility

internal class ComponentShowcaseDialog(project: Project) : DialogWrapper(project) {
    init {
        title = "Jewel Components Showcase"
        init()
        contentPanel.border = JBUI.Borders.empty()
        rootPane.border = JBUI.Borders.empty()
    }

    override fun createSouthPanel(): JComponent? = null

    override fun createCenterPanel(): JComponent {
        val viewModel =
            ComponentsViewModel(
                alwaysVisibleScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default(),
                whenScrollingScrollbarVisibility = ScrollbarVisibility.WhenScrolling.macOs(),
            )

        val dialogPanel = compose {
            val uiSettings = UISettings.getInstance()
            ProvideMarkdownStyling { RootLayout(viewModel, uiSettings) }
        }
        dialogPanel.preferredSize = Dimension(800, 600)
        return dialogPanel
    }

    @Suppress("ViewModelForwarding")
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun RootLayout(viewModel: ComponentsViewModel, uiSettings: UISettings) {
        ComponentsView(
            viewModel = viewModel,
            toolbarButtonMetrics =
                remember(uiSettings.compactMode, ResizeStripeManager.isShowNames()) {
                    IconButtonMetrics(
                        minSize = Toolbar.stripeToolbarButtonSize().toDpSize(),
                        // See com.intellij.openapi.wm.impl.SquareStripeButtonLook.getButtonArc
                        cornerSize =
                            retrieveArcAsCornerSizeOrDefault(
                                "Button.ToolWindow.arc",
                                CornerSize(if (uiSettings.compactMode) 4.dp else 6.dp),
                            ),
                        padding =
                            Toolbar.stripeToolbarButtonIconPadding(
                                    uiSettings.compactMode, // compactMode
                                    ResizeStripeManager.isShowNames(), // showNames
                                )
                                .toPaddingValues(),
                        borderWidth = 0.dp,
                    )
                },
        )
    }
}
