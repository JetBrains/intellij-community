package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.wm.ToolWindow

fun ToolWindow.addComposeTab(
    tabDisplayName: String,
    isLockable: Boolean = true,
    content: @Composable () -> Unit,
) = ComposePanel()
    .apply { setContent(content) }
    .also { contentManager.addContent(contentManager.factory.createContent(it, tabDisplayName, isLockable)) }

