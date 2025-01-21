// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.ideplugin.dialog

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import javax.swing.JComponent
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.bridge.theme.default
import org.jetbrains.jewel.bridge.theme.macOs
import org.jetbrains.jewel.samples.showcase.views.ComponentsView
import org.jetbrains.jewel.samples.showcase.views.ComponentsViewModel
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility

internal class ComponentShowcaseDialog : DialogWrapper(true) {
    init {
        title = "Component Showcase"
        init()
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun createCenterPanel(): JComponent {
        val dialogPanel = JewelComposePanel {
            val viewModel =
                ComponentsViewModel(
                    alwaysVisibleScrollbarVisibility = ScrollbarVisibility.AlwaysVisible.default(),
                    whenScrollingScrollbarVisibility = ScrollbarVisibility.WhenScrolling.macOs(),
                )
            ComponentsView(
                viewModel = viewModel,
                railNavigationModifier =
                    Modifier.size(40.dp).padding(start = 0.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        dialogPanel.preferredSize = Dimension(800, 600)
        return dialogPanel
    }
}
