// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.jewel.bridge.ComposeSemanticsTreeUtils.findFocusedComponent
import org.jetbrains.jewel.foundation.InternalJewelApi

@Internal
@InternalJewelApi
public class ComposePasteProvider : PasteProvider {
    override fun performPaste(dataContext: DataContext) {
        val pasteAction = getPasteAction(dataContext)
        pasteAction?.action?.invoke()
    }

    override fun isPastePossible(dataContext: DataContext): Boolean = getPasteAction(dataContext)?.action != null

    override fun isPasteEnabled(dataContext: DataContext): Boolean = isPastePossible(dataContext)

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun getPasteAction(dataContext: DataContext): AccessibilityAction<() -> Boolean>? {
        val contextComponent = dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val composePanel = contextComponent?.parent as? ComposePanel ?: return null
        val focused = composePanel.findFocusedComponent() ?: return null

        return focused.config.getOrNull(SemanticsActions.PasteText)
    }
}
